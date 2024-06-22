package matyfou.zencraftpriceviewer;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "Zenprice/config")
public class ZenConfig implements ConfigData
{
    @ConfigEntry.Gui.Tooltip
    public boolean serverOnlyOption = true;
    public boolean excelAutoUpdate = true;
    public String priceText = "Prix :";
    public String chestPriceText = "| Prix :";

    @ConfigEntry.Gui.Excluded
    public String odsFileDownloadLink_GITHUB_EXCEL_RAW_URL = "https://raw.githubusercontent.com/Matyfou/ZenpriceTDP/main/table_des_prix.ods";

    @ConfigEntry.Gui.Excluded
    public String odsFileDownloadLink_GITHUB_API_URL = "https://api.github.com/repos/Matyfou/ZenpriceTDP/contents/table_des_prix.ods";
}
