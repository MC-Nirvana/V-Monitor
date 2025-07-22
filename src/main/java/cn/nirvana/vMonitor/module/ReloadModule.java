package cn.nirvana.vMonitor.module;

import cn.nirvana.vMonitor.loader.ConfigFileLoader;
import cn.nirvana.vMonitor.loader.LanguageFileLoader;

import com.velocitypowered.api.command.CommandSource;

import net.kyori.adventure.text.minimessage.MiniMessage;

public class ReloadModule {
    private final ConfigFileLoader configFileLoader;
    private final LanguageFileLoader languageFileLoader;
    private final MiniMessage miniMessage;

    public ReloadModule(ConfigFileLoader configFileLoader, LanguageFileLoader languageFileLoader, MiniMessage miniMessage) {
        this.configFileLoader = configFileLoader;
        this.languageFileLoader = languageFileLoader;
        this.miniMessage = miniMessage;
    }

    public void executeReload(CommandSource source) {
        boolean configReloaded = false;
        boolean langReloaded = false;

        if (configFileLoader != null) {
            try {
                configFileLoader.reloadConfig(); // 调用新的 reloadConfig 方法
                configReloaded = true;
            } catch (Exception e) { // 捕获可能从 loadConfig 抛出的异常
                source.sendMessage(miniMessage.deserialize("<red>Failed to reload config: " + e.getMessage() + "</red>"));
            }
        } else {
            source.sendMessage(miniMessage.deserialize("<red>Configuration loader not available for reload.</red>"));
        }

        if (languageFileLoader != null && configReloaded) { // 只有配置成功重载后才尝试重载语言文件，因为语言文件依赖配置
            try {
                languageFileLoader.reloadLanguage(); // 调用新的 reloadLanguage 方法
                langReloaded = true;
            } catch (Exception e) { // 捕获可能从 loadLanguage 抛出的异常
                source.sendMessage(miniMessage.deserialize("<red>Failed to reload language: " + e.getMessage() + "</red>"));
            }
        } else if (!configReloaded) {
            source.sendMessage(miniMessage.deserialize("<yellow>Skipping language reload due to config reload failure.</yellow>"));
        } else {
            source.sendMessage(miniMessage.deserialize("<red>Language loader not available for reload.</red>"));
        }

        if (configReloaded && langReloaded) {
            source.sendMessage(miniMessage.deserialize(languageFileLoader.getMessage("global.reload_success")));
        } else {
            source.sendMessage(miniMessage.deserialize("<red>Reload completed with errors. Check console for details.</red>"));
        }
    }
}