package org.wallentines.skinsetter.common;

import org.wallentines.midnightcore.api.MidnightCoreAPI;
import org.wallentines.midnightcore.api.player.DataProvider;
import org.wallentines.midnightcore.api.text.LangProvider;
import org.wallentines.midnightcore.api.text.LangRegistry;
import org.wallentines.midnightcore.common.util.FileUtil;
import org.wallentines.mdcfg.ConfigSection;
import org.wallentines.midnightcore.api.FileConfig;
import org.wallentines.skinsetter.api.SavedSkin;
import org.wallentines.skinsetter.api.SkinRegistry;
import org.wallentines.skinsetter.api.SkinSetterAPI;
import org.wallentines.skinsetter.common.integration.HideAndSeekIntegration;

import java.io.File;
import java.nio.file.Path;

public class SkinSetterImpl extends SkinSetterAPI {

    private final FileConfig config;
    private final File dataFolder;

    private final SkinRegistryImpl skinRegistry;
    private final LangProvider langProvider;
    private final DataProvider dataProvider;

    private SavedSkin defaultSkin;
    private boolean persistence;


    public SkinSetterImpl(Path dataFolder, ConfigSection langDefaults) {

        super();

        this.dataFolder = FileUtil.tryCreateDirectory(dataFolder);
        if(this.dataFolder == null) {
            throw new IllegalStateException("Unable to create data folder!");
        }

        this.config = FileConfig.findOrCreate("config", this.dataFolder, Constants.CONFIG_DEFAULTS);
        this.config.getRoot().fill(Constants.CONFIG_DEFAULTS);

        Constants.registerIntegrations();

        File skinFolder = FileUtil.tryCreateDirectory(dataFolder.resolve("skins"));
        if(skinFolder == null) {
            throw new IllegalStateException("Unable to create skin folder!");
        }

        this.langProvider = new LangProvider(FileUtil.tryCreateDirectory(dataFolder.resolve("lang")), LangRegistry.fromConfigSection(langDefaults));
        this.dataProvider = new DataProvider(FileUtil.tryCreateDirectory(dataFolder.resolve("data")));
        this.skinRegistry = new SkinRegistryImpl(skinFolder);

        if(this.config.getRoot().hasList("skins")) {
            MidnightCoreAPI.getLogger().info("Attempting to upgrade data from a pre-3.0 version of SkinSetter...");
            SavedSkinImpl.updateFromOldConfig(this.config.getRoot(), this.skinRegistry);
        }

        try {
            Class.forName("org.wallentines.hideandseek.api.event.ClassApplyEvent");
            HideAndSeekIntegration.setup();
        } catch (ClassNotFoundException ex) {
            // Ignore
        }

        defaultSkin = skinRegistry.getSkin(config.getRoot().getString(Constants.CONFIG_KEY_DEFAULT_SKIN));
        persistence = config.getRoot().getBoolean(Constants.CONFIG_KEY_PERSISTENCE);

        this.config.save();
    }

    public void reload() {

        skinRegistry.reloadAll();

        defaultSkin = skinRegistry.getSkin(config.getRoot().getString(Constants.CONFIG_KEY_DEFAULT_SKIN));
        persistence = config.getRoot().getBoolean(Constants.CONFIG_KEY_PERSISTENCE);
    }

    @Override
    public ConfigSection getConfig() {

        return config.getRoot();
    }

    @Override
    public void saveConfig() {

        config.save();
    }

    @Override
    public File getDataFolder() {
        return dataFolder;
    }

    @Override
    public SkinRegistry getSkinRegistry() {
        return skinRegistry;
    }

    @Override
    public SavedSkin getDefaultSkin() {
        return defaultSkin;
    }

    @Override
    public void setDefaultSkin(SavedSkin skin) {
        this.defaultSkin = skin;
    }

    @Override
    public boolean isPersistenceEnabled() {
        return persistence;
    }

    @Override
    public void setPersistenceEnabled(boolean persistence) {
        this.persistence = persistence;
    }

    @Override
    public LangProvider getLangProvider() {
        return langProvider;
    }

    @Override
    public DataProvider getDataProvider() {
        return dataProvider;
    }

}
