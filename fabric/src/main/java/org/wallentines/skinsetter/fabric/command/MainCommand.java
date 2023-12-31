package org.wallentines.skinsetter.fabric.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.wallentines.midnightcore.api.MidnightCoreAPI;
import org.wallentines.midnightcore.api.item.MItemStack;
import org.wallentines.midnightcore.api.module.skin.Skin;
import org.wallentines.midnightcore.api.module.skin.SkinModule;
import org.wallentines.midnightcore.api.module.skin.Skinnable;
import org.wallentines.midnightcore.api.player.DataProvider;
import org.wallentines.midnightcore.api.player.MPlayer;
import org.wallentines.midnightcore.api.server.MServer;
import org.wallentines.midnightcore.api.text.CustomPlaceholder;
import org.wallentines.midnightcore.api.text.CustomPlaceholderInline;
import org.wallentines.midnightcore.api.text.MComponent;
import org.wallentines.midnightcore.common.util.MojangUtil;
import org.wallentines.midnightcore.fabric.item.FabricItem;
import org.wallentines.midnightcore.fabric.player.FabricPlayer;
import org.wallentines.midnightcore.fabric.util.CommandUtil;
import org.wallentines.midnightcore.fabric.util.ConversionUtil;
import org.wallentines.skinsetter.api.EditableSkin;
import org.wallentines.skinsetter.api.SavedSkin;
import org.wallentines.skinsetter.api.SkinSetterAPI;
import org.wallentines.skinsetter.common.Constants;
import org.wallentines.skinsetter.common.SavedSkinImpl;
import org.wallentines.skinsetter.common.SkinSetterImpl;
import org.wallentines.skinsetter.common.util.GuiUtil;

import java.util.List;
import java.util.UUID;

public class MainCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {

        LiteralArgumentBuilder<CommandSourceStack> cmd = Commands.literal("skin")
            .requires(Permissions.require("skinsetter.command", 2))
            .then(Commands.literal("set")
                .requires(Permissions.require("skinsetter.command.set", 2))
                .then(Commands.argument("entities", EntityArgument.entities())
                    .executes(context -> executeSet(context, context.getArgument("entities", EntitySelector.class).findEntities(context.getSource()), null, false))
                    .then(Commands.argument("skin", StringArgumentType.word())
                        .suggests(((context, builder) -> {

                            MPlayer player = null;
                            try {
                                player = FabricPlayer.wrap(context.getSource().getPlayerOrException());
                            } catch (CommandSyntaxException ex) {
                                // Ignore
                            }
                            return SharedSuggestionProvider.suggest(SkinSetterAPI.getInstance().getSkinRegistry().getSkinNames(player), builder);
                        }))
                        .executes(context -> executeSet(context, context.getArgument("entities", EntitySelector.class).findEntities(context.getSource()), context.getArgument("skin", String.class), false))
                        .then(Commands.literal("-o")
                            .requires(Permissions.require("skinsetter.command.set.online", 2))
                            .executes(context -> executeSet(context, context.getArgument("entities", EntitySelector.class).findEntities(context.getSource()), context.getArgument("skin", String.class), true))
                        )
                    )
                )
            )
            .then(Commands.literal("reset")
                .requires(Permissions.require("skinsetter.command.reset", 2))
                .then(Commands.argument("entities", EntityArgument.entities())
                    .executes(context -> executeReset(context, context.getArgument("entities", EntitySelector.class).findEntities(context.getSource())))
                )
            )
            .then(Commands.literal("save")
                .requires(Permissions.require("skinsetter.command.save", 2))
                .then(Commands.argument("entity", EntityArgument.entity())
                    .then(Commands.argument("id", StringArgumentType.word())
                        .executes(context -> executeSave(context, context.getArgument("entity", EntitySelector.class).findSingleEntity(context.getSource()), context.getArgument("id", String.class)))
                    )
                )
            )
            .then(Commands.literal("setdefault")
                .requires(Permissions.require("skinsetter.command.setdefault", 2))
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests(((context, builder) -> {

                        MPlayer player = null;
                        try {
                            player = FabricPlayer.wrap(context.getSource().getPlayerOrException());
                        } catch (CommandSyntaxException ex) {
                            // Ignore
                        }
                        return SharedSuggestionProvider.suggest(SkinSetterAPI.getInstance().getSkinRegistry().getSkinNames(player), builder);
                    }))
                    .executes(context -> executeSetDefault(context, context.getArgument("id", String.class)))
                )
            )
            .then(Commands.literal("cleardefault")
                .requires(Permissions.require("skinsetter.command.setdefault", 2))
                .executes(MainCommand::executeClearDefault)
            )
            .then(Commands.literal("persistence")
                .requires(Permissions.require("skinsetter.command.persistence", 2))
                .then(Commands.literal("enable")
                    .executes(MainCommand::executePersistenceEnable)
                )
                .then(Commands.literal("disable")
                    .executes(MainCommand::executePersistenceDisable)
                )
            )
            .then(Commands.literal("reload")
                .requires(Permissions.require("skinsetter.command.reload", 2))
                .executes(MainCommand::executeReload)
            )
            .then(Commands.literal("setrandom")
                .requires(Permissions.require("skinsetter.command.setrandom", 2))
                .then(Commands.argument("entities", EntityArgument.entities())
                    .executes(context -> executeSetRandom(context, context.getArgument("entities", EntitySelector.class).findEntities(context.getSource()), null))
                    .then(Commands.argument("group", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            if(context.getSource().getPlayer() == null) return SharedSuggestionProvider.suggest(SkinSetterAPI.getInstance().getSkinRegistry().getGroupNames(), builder);
                            return SharedSuggestionProvider.suggest(SkinSetterAPI.getInstance().getSkinRegistry().getGroupNames(FabricPlayer.wrap(context.getSource().getPlayer())), builder);
                        })
                        .executes(context -> executeSetRandom(context, context.getArgument("entities", EntitySelector.class).findEntities(context.getSource()), context.getArgument("group", String.class)))
                    )
                )
            )
            .then(Commands.literal("edit")
                .requires(Permissions.require("skinsetter.command.edit", 2))
                .then(Commands.argument("skin", StringArgumentType.word())
                    .suggests(((context, builder) -> {

                        MPlayer player = null;
                        try {
                            player = FabricPlayer.wrap(context.getSource().getPlayerOrException());
                        } catch (CommandSyntaxException ex) {
                            // Ignore
                        }
                        return SharedSuggestionProvider.suggest(SkinSetterAPI.getInstance().getSkinRegistry().getSkinNames(player), builder);
                    }))
                    .then(Commands.literal("name")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                            .executes(context -> executeEditName(context, context.getArgument("skin", String.class), context.getArgument("name", String.class)))
                        )
                    )
                    .then(Commands.literal("groups")
                        .then(Commands.literal("add")
                            .then(Commands.argument("group", StringArgumentType.word())
                                .executes(context -> executeEditAddGroup(context, context.getArgument("skin", String.class), context.getArgument("group", String.class)))
                            )
                        )
                        .then(Commands.literal("remove")
                            .then(Commands.argument("group", StringArgumentType.word())
                                .suggests((context, builder) -> {

                                    SavedSkin skin = SkinSetterAPI.getInstance().getSkinRegistry().getSkin(context.getArgument("skin", String.class));
                                    if(skin != null) return SharedSuggestionProvider.suggest(skin.getGroups(), builder);

                                    return null;
                                })
                                .executes(context -> executeEditRemoveGroup(context, context.getArgument("skin", String.class), context.getArgument("group", String.class)))
                            )
                        )
                    )
                    .then(Commands.literal("item")
                        .then(Commands.literal("save")
                            .executes(context -> executeEditSaveItem(context, context.getArgument("skin", String.class), context.getSource().getPlayerOrException().getMainHandItem()))
                            .then(Commands.argument("item", ItemArgument.item(buildContext))
                                .executes(context -> executeEditSaveItem(context, context.getArgument("skin", String.class), context.getArgument("item", ItemStack.class)))
                            )
                        )
                        .then(Commands.literal("clear")
                            .executes(context -> executeEditClearItem(context, context.getArgument("skin", String.class)))
                        )
                    )
                    .then(Commands.literal("excludeInRandom")
                        .then(Commands.argument("exclude", BoolArgumentType.bool())
                            .executes(context -> executeEditExcludeInRandom(context, context.getArgument("skin", String.class), context.getArgument("exclude", Boolean.class)))
                        )
                    )
                )
            )
            .then(Commands.literal("head")
                .requires(Permissions.require("skinsetter.command.head", 2))
                .then(Commands.argument("skin", StringArgumentType.word())
                    .suggests(((context, builder) -> {
                        MPlayer player = null;
                        try {
                            player = FabricPlayer.wrap(context.getSource().getPlayerOrException());
                        } catch (CommandSyntaxException ex) {
                            // Ignore
                        }
                        return SharedSuggestionProvider.suggest(SkinSetterAPI.getInstance().getSkinRegistry().getSkinNames(player), builder);
                    }))
                    .then(Commands.argument("players", EntityArgument.players())
                        .executes(context -> executeHead(context, context.getArgument("players", EntitySelector.class).findPlayers(context.getSource()), context.getArgument("skin", String.class)))
                    )
                )
            );

        dispatcher.register(cmd);
    }



    private static int executeSet(CommandContext<CommandSourceStack> context, List<? extends Entity> players, String skin, boolean online) {

        if(players == null || players.isEmpty()) return 0;

        try {
            if (skin == null) {

                MPlayer player = null;
                if (context.getSource().getPlayer() != null) {
                    player = FabricPlayer.wrap(context.getSource().getPlayer());
                }
                if(player == null) {
                    return 0;
                }

                GuiUtil.openGUI(player,
                        SkinSetterAPI.getInstance().getLangProvider(),
                        SkinSetterAPI.getInstance().getSkinRegistry().getSkins(player, null),
                        sk -> executeSet(context, players, sk.getId(), false)
                );
                return players.size();
            }

            MinecraftServer server = context.getSource().getServer();
            SavedSkin s = SkinSetterAPI.getInstance().getSkinRegistry().getSkin(skin);
            Skin sk;

            if ((s == null || online) && Permissions.check(context.getSource(), "skinsetter.command.set.online", 2)) {

                ServerPlayer player = server.getPlayerList().getPlayerByName(skin);
                if (player == null || (online && !server.usesAuthentication())) {

                    return executeSetOnline(context, players, skin);

                } else {

                    FabricPlayer fp = FabricPlayer.wrap(player);
                    sk = fp.getServer().getModule(SkinModule.class).getOriginalSkin(fp);
                }
            } else {

                if (s == null) {
                    CommandUtil.sendCommandFailure(context, SkinSetterAPI.getInstance().getLangProvider(), "command.error.invalid_skin");
                    return 0;
                }

                if (context.getSource().getPlayer() != null) {
                    if (!s.canUse(FabricPlayer.wrap(context.getSource().getPlayer()))) {
                        SkinSetterAPI.getLogger().info("User cannot use");
                        CommandUtil.sendCommandFailure(context, SkinSetterAPI.getInstance().getLangProvider(), "command.error.invalid_skin");
                        return 0;
                    }
                }

                sk = s.getSkin();
            }

            for (Entity ent : players) {
                ((Skinnable) ent).setSkin(sk);
            }

            CommandUtil.sendCommandSuccess(context, SkinSetterAPI.getInstance().getLangProvider(), false, players.size() == 1 ? "command.set.result.single" : "command.set.result.multiple", CustomPlaceholderInline.create("count", players.size() + ""), nameOf(players.get(0)));

        } catch (Throwable th) {
            th.printStackTrace();
            throw th;
        }
        return players.size();
    }

    private static int executeSetOnline(CommandContext<CommandSourceStack> context, List<? extends Entity> players, String skin) {

        if(players == null || players.isEmpty()) return 0;

        if(!Permissions.check(context.getSource(), "skinsetter.command.set.online", 2)) {
            CommandUtil.sendCommandFailure(context, SkinSetterAPI.getInstance().getLangProvider(),"command.error.invalid_skin");
            return 0;
        }

        CommandUtil.sendCommandSuccess(context, SkinSetterAPI.getInstance().getLangProvider(), false, "command.set.online", CustomPlaceholderInline.create("name", skin));

        MinecraftServer server = context.getSource().getServer();
        new Thread(() -> {
            UUID uid = MojangUtil.getUUID(skin);
            Skin sk = MojangUtil.getSkin(uid);
            if(sk == null) {
                CommandUtil.sendCommandSuccess(context, SkinSetterAPI.getInstance().getLangProvider(), false, "command.error.invalid_name");
                return;
            }

            server.submit(() -> {
                for(Entity ent : players) {
                    if(ent instanceof Skinnable) {
                        ((Skinnable) ent).setSkin(sk);
                    } else {
                        MidnightCoreAPI.getLogger().warn(ent.getUUID() + " is not a skinnable entity!");
                    }
                }

                CommandUtil.sendCommandSuccess(context, SkinSetterAPI.getInstance().getLangProvider(), false, players.size() == 1 ? "command.set.result.single" : "command.set.result.multiple", CustomPlaceholder.create("entity_name", ConversionUtil.toMComponent(players.get(0).getName())));
            });
        }).start();

        return players.size();
    }

    private static int executeReset(CommandContext<CommandSourceStack> context, List<? extends Entity> players) {

        if(players == null || players.isEmpty()) return 0;

        for(Entity ent : players) {
            if(ent instanceof Skinnable) ((Skinnable) ent).resetSkin();
        }

        CommandUtil.sendCommandSuccess(context, SkinSetterAPI.getInstance().getLangProvider(), false, players.size() == 1 ? "command.reset.result.single" : "command.reset.result.multiple", CustomPlaceholderInline.create("count", players.size()+""), nameOf(players.get(0)));

        return players.size();
    }

    private static int executeSave(CommandContext<CommandSourceStack> context, Entity player, String id) {

        if(!(player instanceof Skinnable)) {

            CommandUtil.sendCommandSuccess(context, SkinSetterAPI.getInstance().getLangProvider(), false,"command.error.invalid_name");
            return 0;
        }

        Skin mpSkin = ((Skinnable) player).getSkin();
        if(mpSkin == null) {
            CommandUtil.sendCommandSuccess(context, SkinSetterAPI.getInstance().getLangProvider(), false,"command.error.null_skin");
            return 0;
        }

        SavedSkin sk = new SavedSkinImpl(id, mpSkin);
        SkinSetterAPI.getInstance().getSkinRegistry().registerSkin(sk);
        CommandUtil.sendCommandSuccess(context, SkinSetterAPI.getInstance().getLangProvider(), false,"command.save.result", id, sk, nameOf(player));

        return 1;
    }

    private static int executeSetDefault(CommandContext<CommandSourceStack> context, String skinId) {

        SavedSkin skin = SkinSetterAPI.getInstance().getSkinRegistry().getSkin(skinId);

        if(skin == null) {
            CommandUtil.sendCommandFailure(context, SkinSetterAPI.getInstance().getLangProvider(), "command.error.invalid_skin");
            return 0;
        }

        SkinSetterAPI.getInstance().setDefaultSkin(skin);
        SkinSetterAPI.getInstance().getConfig().set("default_skin", skinId);
        SkinSetterAPI.getInstance().saveConfig();

        CommandUtil.sendCommandSuccess(context, SkinSetterAPI.getInstance().getLangProvider(), false,"command.setdefault.result", skin, CustomPlaceholderInline.create("id", skinId));

        return 1;
    }

    private static int executeClearDefault(CommandContext<CommandSourceStack> context) {

        SkinSetterAPI.getInstance().setDefaultSkin(null);
        SkinSetterAPI.getInstance().getConfig().set("default_skin", "");
        SkinSetterAPI.getInstance().saveConfig();

        CommandUtil.sendCommandSuccess(context, SkinSetterAPI.getInstance().getLangProvider(), false,"command.cleardefault.result");

        return 1;
    }

    private static int executePersistenceEnable(CommandContext<CommandSourceStack> context) {

        SkinSetterAPI.getInstance().setPersistenceEnabled(true);
        SkinSetterAPI.getInstance().getConfig().set("persistent_skins", true);
        SkinSetterAPI.getInstance().saveConfig();

        CommandUtil.sendCommandSuccess(context, SkinSetterAPI.getInstance().getLangProvider(), false,"command.persistence.result.enable");

        return 1;
    }

    private static int executePersistenceDisable(CommandContext<CommandSourceStack> context) {

        SkinSetterAPI.getInstance().setPersistenceEnabled(false);
        SkinSetterAPI.getInstance().getConfig().set("persistent_skins", false);
        SkinSetterAPI.getInstance().saveConfig();

        CommandUtil.sendCommandSuccess(context, SkinSetterAPI.getInstance().getLangProvider(), false,"command.persistence.result.disable");

        MServer server = MidnightCoreAPI.getRunningServer();
        if(server == null) return 0;

        DataProvider prov = SkinSetterAPI.getInstance().getDataProvider();
        for(MPlayer pl : server.getPlayerManager()) {

            prov.getData(pl).remove(Constants.DEFAULT_NAMESPACE);
            prov.saveData(pl);
        }

        return 1;
    }

    private static int executeReload(CommandContext<CommandSourceStack> context) {

        long time = System.currentTimeMillis();
        ((SkinSetterImpl) SkinSetterAPI.getInstance()).reload();
        time = System.currentTimeMillis() - time;

        CommandUtil.sendCommandSuccess(context, SkinSetterAPI.getInstance().getLangProvider(), false,"command.reload.result", CustomPlaceholderInline.create("time", time+""));

        return (int) time;

    }

    private static int executeSetRandom(CommandContext<CommandSourceStack> context, List<? extends Entity> entities, String group) throws CommandSyntaxException {

        if(entities == null || entities.isEmpty()) return 0;

        MPlayer mpl = null;
        if(context.getSource().getPlayer() != null) {
            mpl = FabricPlayer.wrap(context.getSource().getPlayerOrException());
        }

        for(Entity ent : entities) {

            if(!(ent instanceof Skinnable)) continue;

            SavedSkin s = SkinSetterAPI.getInstance().getSkinRegistry().getRandomSkin(mpl, group);
            if(s == null) {

                CommandUtil.sendCommandFailure(context, SkinSetterAPI.getInstance().getLangProvider(), "command.error.no_saved");
                return 0;
            }

            ((Skinnable) ent).setSkin(s.getSkin());
        }

        CommandUtil.sendCommandSuccess(context, SkinSetterAPI.getInstance().getLangProvider(), false, entities.size() == 1 ? "command.set.result.single" : "command.set.result.multiple", CustomPlaceholderInline.create("count", entities.size()+""), nameOf(entities.get(0)));

        return entities.size();
    }

    private static int executeEditExcludeInRandom(CommandContext<CommandSourceStack> context, String skin, Boolean exclude) {

        EditableSkin s = SkinSetterAPI.getInstance().getSkinRegistry().createEditableSkin(skin);
        if(s == null) {
            CommandUtil.sendCommandFailure(context, SkinSetterAPI.getInstance().getLangProvider(), "command.error.invalid_skin");
            return 0;
        }

        s.excludeFromRandom(exclude);
        s.save();

        String key = exclude ? "command.edit.excludeInRandom.result.enabled" : "command.edit.excludeInRandom.result.disabled";
        CommandUtil.sendCommandSuccess(context, SkinSetterAPI.getInstance().getLangProvider(), false, key);

        return 1;
    }

    private static int executeEditClearItem(CommandContext<CommandSourceStack> context, String skin) {

        EditableSkin s = SkinSetterAPI.getInstance().getSkinRegistry().createEditableSkin(skin);
        if(s == null) {
            CommandUtil.sendCommandFailure(context, SkinSetterAPI.getInstance().getLangProvider(), "command.error.invalid_skin");
            return 0;
        }

        s.setDisplayItem(null);
        s.save();

        CommandUtil.sendCommandSuccess(context, SkinSetterAPI.getInstance().getLangProvider(), false, "command.edit.item.clear.result", s);

        return 1;
    }

    private static int executeEditSaveItem(CommandContext<CommandSourceStack> context, String skin, ItemStack is) {

        EditableSkin s = SkinSetterAPI.getInstance().getSkinRegistry().createEditableSkin(skin);
        if(s == null) {
            CommandUtil.sendCommandFailure(context, SkinSetterAPI.getInstance().getLangProvider(), "command.error.invalid_skin");
            return 0;
        }

        if(is == null || is.isEmpty()) {
            CommandUtil.sendCommandFailure(context, SkinSetterAPI.getInstance().getLangProvider(), "command.error.invalid_item");
            return 0;
        }

        s.setDisplayItem(new FabricItem(is));
        s.save();

        CommandUtil.sendCommandSuccess(context, SkinSetterAPI.getInstance().getLangProvider(), false, "command.edit.item.save.result", s);

        return 1;

    }

    private static int executeEditRemoveGroup(CommandContext<CommandSourceStack> context, String skin, String group) {

        EditableSkin s = SkinSetterAPI.getInstance().getSkinRegistry().createEditableSkin(skin);
        if(s == null) {
            CommandUtil.sendCommandFailure(context, SkinSetterAPI.getInstance().getLangProvider(), "command.error.invalid_skin");
            return 0;
        }

        s.removeGroup(group);
        s.save();

        CommandUtil.sendCommandSuccess(context, SkinSetterAPI.getInstance().getLangProvider(), false, "command.edit.groups.result", s);

        return 1;

    }

    private static int executeEditAddGroup(CommandContext<CommandSourceStack> context, String skin, String group) {

        EditableSkin s = SkinSetterAPI.getInstance().getSkinRegistry().createEditableSkin(skin);
        if(s == null) {
            CommandUtil.sendCommandFailure(context, SkinSetterAPI.getInstance().getLangProvider(), "command.error.invalid_skin");
            return 0;
        }

        s.addGroup(group);
        s.save();

        CommandUtil.sendCommandSuccess(context, SkinSetterAPI.getInstance().getLangProvider(), false, "command.edit.groups.result", s);

        return 1;

    }

    private static int executeEditName(CommandContext<CommandSourceStack> context, String skin, String name) {

        try {
            EditableSkin s = SkinSetterAPI.getInstance().getSkinRegistry().createEditableSkin(skin);
            if (s == null) {
                CommandUtil.sendCommandFailure(context, SkinSetterAPI.getInstance().getLangProvider(), "command.error.invalid_skin");
                return 0;
            }

            MComponent newName = MComponent.parse(name);
            s.setName(newName);
            s.save();

            CommandUtil.sendCommandSuccess(context, SkinSetterAPI.getInstance().getLangProvider(), false, "command.edit.name.result", s);
        } catch (Throwable th) {
            th.printStackTrace();
            return 0;
        }
        return 1;
    }

    private static int executeHead(CommandContext<CommandSourceStack> context, List<ServerPlayer> players, String skin) {

        SavedSkin s = SkinSetterAPI.getInstance().getSkinRegistry().getSkin(skin);
        if(s == null) {
            CommandUtil.sendCommandFailure(context, SkinSetterAPI.getInstance().getLangProvider(), "command.error.invalid_skin");
            return 0;
        }

        MItemStack is = s.getHeadItem();
        for(ServerPlayer pl : players) {
            pl.addItem(((FabricItem) is).getInternal());
        }

        CommandUtil.sendCommandSuccess(context, SkinSetterAPI.getInstance().getLangProvider(), false, "command.head.result", s);

        return players.size();
    }

    private static CustomPlaceholder nameOf(Entity ent) {
        return CustomPlaceholder.create("entity_name", ConversionUtil.toMComponent(ent.getName()));
    }
    


}
