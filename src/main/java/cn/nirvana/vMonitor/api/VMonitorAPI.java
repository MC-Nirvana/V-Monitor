package cn.nirvana.vMonitor.api;

public class VMonitorAPI {
    private static VMonitorAPI instance;
    private VMonitorAPI() {
    }
    public static VMonitorAPI getInstance() {
        if (instance == null) {
            instance = new VMonitorAPI();
        }
        return instance;
    }

    public int getOnlinePlayerCount() {
        System.out.println("API Call: getOnlinePlayerCount()");
        return 0;
    }

    public static void setInstance(VMonitorAPI apiInstance) {
        if (instance != null) {
            throw new IllegalStateException("VMonitorAPI instance already set!");
        }
        instance = apiInstance;
    }
}