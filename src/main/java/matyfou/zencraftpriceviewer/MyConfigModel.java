package matyfou.zencraftpriceviewer;


import io.wispforest.owo.config.annotation.Config;
import io.wispforest.owo.config.annotation.ExcludeFromScreen;
import io.wispforest.owo.config.annotation.Modmenu;
import io.wispforest.owo.config.annotation.SectionHeader;
import net.minecraft.text.Text;

@Modmenu(modId = "zencraftprice")
@Config(name = "Zenprice/ZenpriceConfig", wrapperName = "MyConfig")
public class MyConfigModel
{
    @SectionHeader("General")
    public boolean serverOnlyOption = true;
    public boolean excelAutoUpdate = true;
    public String priceText = "Prix : ";
    public String chestPriceText = "| Prix : ";

    @ExcludeFromScreen
    public String odsFileDownloadLink_GITHUB_EXCEL_RAW_URL = "https://raw.githubusercontent.com/Matyfou/ZenpriceTDP/main/table_des_prix.ods";

    @ExcludeFromScreen
    public String odsFileDownloadLink_GITHUB_API_URL = "https://api.github.com/repos/Matyfou/ZenpriceTDP/contents/table_des_prix.ods";
}
