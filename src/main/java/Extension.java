import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.Registration;
import burp.api.montoya.extension.ExtensionUnloadingHandler;
import burp.api.montoya.logging.Logging;
import py4j.GatewayServer;
import burp.api.montoya.ui.settings.SettingsPanelBuilder;
import burp.api.montoya.ui.settings.SettingsPanelPersistence;
import burp.api.montoya.ui.settings.SettingsPanelSetting;
import burp.api.montoya.ui.settings.SettingsPanelWithData;
import py4j.GatewayServer.GatewayServerBuilder;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class Extension implements BurpExtension, ExtensionUnloadingHandler {
    private static final String EXTENSION_NAME = "Burp Python Gateway Montoya";
    private static final String KEY_JAVA_LISTEN_ADDRESS = "Java Listen Address";
    private static final String KEY_JAVA_LISTEN_PORT = "Java Listen Port";
    private static final String KEY_PYTHON_CALLBACK_ADDRESS = "Python Callback Address";
    private static final String KEY_PYTHON_CALLBACK_PORT = "Python Callback Port";
    private static final String KEY_AUTH_TOKEN = "Auth Token";
    private static final String KEY_READ_TIMEOUT = "Read Timeout";
    private static final String KEY_CONNECT_TIMEOUT = "Connect Timeout";

    private GatewayServer gatewayServer = null;
    private Registration settingsPanelRegistration = null;
    private SettingsPanelWithData settingsPanel = null;

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName(EXTENSION_NAME);
        api.extension().registerUnloadingHandler(this);

        Logging logging = api.logging();

        settingsPanel = SettingsPanelBuilder.settingsPanel()
                .withPersistence(SettingsPanelPersistence.USER_SETTINGS)
                .withTitle(EXTENSION_NAME)
                .withDescription("""
                        Connect to Burp with Py4j and get full access to the Montoya API.
                        
                        Note: reload the extension after changing the settings!""")
                .withKeywords("Python", "Settings", "Gateway", "Monotya")
                .withPersistence(SettingsPanelPersistence.PROJECT_SETTINGS)
                .withSettings(
                        SettingsPanelSetting.stringSetting(KEY_JAVA_LISTEN_ADDRESS, GatewayServer.DEFAULT_ADDRESS),
                        SettingsPanelSetting.integerSetting(KEY_JAVA_LISTEN_PORT, GatewayServer.DEFAULT_PORT),
                        SettingsPanelSetting.stringSetting(KEY_PYTHON_CALLBACK_ADDRESS, "127.0.0.1"),
                        SettingsPanelSetting.integerSetting(KEY_PYTHON_CALLBACK_PORT, GatewayServer.DEFAULT_PYTHON_PORT),
                        SettingsPanelSetting.stringSetting(KEY_AUTH_TOKEN, ""),
                        SettingsPanelSetting.integerSetting(KEY_READ_TIMEOUT, GatewayServer.DEFAULT_READ_TIMEOUT),
                        SettingsPanelSetting.integerSetting(KEY_CONNECT_TIMEOUT, GatewayServer.DEFAULT_CONNECT_TIMEOUT)
                )
                .build();

        // Register the settings panel
        settingsPanelRegistration = api.userInterface().registerSettingsPanel(settingsPanel);

        try {
            GatewayServerBuilder builder = new GatewayServerBuilder();
            builder.entryPoint(api);
            builder.javaAddress(InetAddress.getByName(settingsPanel.getString(KEY_JAVA_LISTEN_ADDRESS)));
            builder.javaPort(settingsPanel.getInteger(KEY_JAVA_LISTEN_PORT));


            if (!settingsPanel.getString(KEY_AUTH_TOKEN).isBlank()) {
                builder.authToken(settingsPanel.getString(KEY_AUTH_TOKEN));
            }

            if (!settingsPanel.getString(KEY_PYTHON_CALLBACK_ADDRESS).isBlank()) {
                builder.callbackClient(settingsPanel.getInteger(KEY_PYTHON_CALLBACK_PORT),
                        InetAddress.getByName(settingsPanel.getString(KEY_PYTHON_CALLBACK_ADDRESS)));
            }

            gatewayServer = builder.build();
            gatewayServer.start();
        } catch (UnknownHostException e) {
            logging.logToError(e.toString());
            return;
        }

        logging.logToOutput(String.format("""
                        Burp Python Gateway (Montoya) Loaded!
                            %s -> %s
                            %s -> %d
                            %s -> %s
                            %s -> %d
                            %s -> %s
                            %s -> %d
                            %s -> %d
                        
                        You can change these in Burp settings! Make sure to reload the extension.""",
                KEY_JAVA_LISTEN_ADDRESS, settingsPanel.getString(KEY_JAVA_LISTEN_ADDRESS),
                KEY_JAVA_LISTEN_PORT, settingsPanel.getInteger(KEY_JAVA_LISTEN_PORT),
                KEY_PYTHON_CALLBACK_ADDRESS, settingsPanel.getString(KEY_PYTHON_CALLBACK_ADDRESS),
                KEY_PYTHON_CALLBACK_PORT, settingsPanel.getInteger(KEY_PYTHON_CALLBACK_PORT),
                KEY_AUTH_TOKEN, settingsPanel.getString(KEY_AUTH_TOKEN),
                KEY_READ_TIMEOUT, settingsPanel.getInteger(KEY_READ_TIMEOUT),
                KEY_CONNECT_TIMEOUT, settingsPanel.getInteger(KEY_CONNECT_TIMEOUT)
        ));
    }

    @Override
    public void extensionUnloaded() {
        if (gatewayServer != null) {
            gatewayServer.shutdown();
        }

        if (settingsPanelRegistration != null) {
            settingsPanelRegistration.deregister();
        }
    }
}