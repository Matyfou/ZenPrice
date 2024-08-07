package matyfou.zencraftpriceviewer.zencraftprice;

import com.github.miachm.sods.Range;
import com.github.miachm.sods.Sheet;
import com.github.miachm.sods.SpreadSheet;

import com.mojang.datafixers.TypeRewriteRule;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import matyfou.zencraftpriceviewer.ZenConfig;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.BlockItem;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.net.HttpURLConnection;
import java.net.URL;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;

import static com.google.common.base.Ascii.toUpperCase;


public class ZencraftPrice implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("zencraftprice");
    private final Map<String, Double> itemPrices = new HashMap<>();

    ZenConfig config;

    boolean IsZenlauncher = false;

    private String CONFIG_DIR = (FabricLoader.getInstance().getConfigDir() + "\\Zenprice");
    private static final String DEFAULT_EXCEL_PATH = "table_des_prix.ods";
    private String CONFIG_EXCEL_PATH = CONFIG_DIR + "\\table_des_prix.ods";

    double totalPrice = 0;

    @Override
    public void onInitialize()
    {
        AutoConfig.register(ZenConfig.class, GsonConfigSerializer::new);

        config = AutoConfig.getConfigHolder(ZenConfig.class).getConfig();

        if(FabricLoader.getInstance().isModLoaded("zenclient"))
        {
            LOGGER.info("Zenlauncher detecter !");
            IsZenlauncher = true;
        }

        if(config.excelAutoUpdate)
        {
            checkAndUpdateExcelFile();
        }
        else
        {
            ExcelFileGet();
        }

        loadPricesFromOds(CONFIG_EXCEL_PATH);

        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) -> {
            if(!config.serverOnlyOption || IsZenlauncher || isConnectedToServer("zencraft"))
            {
                putTitles(stack, lines);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if(!config.serverOnlyOption|| IsZenlauncher || isConnectedToServer("zencraft"))
            {
                setChestName(client);
            }
        });
    }




    private void putTitles(ItemStack stack, List<Text> lines)
    {
            String itemName = toUpperCase(stack.getItem().toString().substring(10)); // retirer le minecraft: au debut
            double itemPrice = 0;
            totalPrice = 0;

            if(itemPrices.get(itemName) == null)
            {
                LOGGER.error("1Item : " + itemName + " pas trouver dans la liste !");
            }
            else
            {
                itemPrice = itemPrices.get(itemName);
            }
            if (itemPrice > 0)
            {
                // Verifier si l'item est une shulker
                if (stack.getItem() instanceof BlockItem)
                {
                    BlockItem blockItem = (BlockItem) stack.getItem();
                    if (blockItem.getBlock() instanceof ShulkerBoxBlock)
                    {
                        totalPrice = getShulkerTotal(stack);
                        if (totalPrice > 0)
                        {
                            itemPrice = totalPrice;
                        }
                    }
                }

                // Ajouter le prix des enchantements
                itemPrice += getEnchantementsPrice(stack);

                // Arondir au deux decimal
                itemPrice = Math.round(itemPrice * 100.0) / 100.0;

                // Ne pas mettre de prix si c'est un item de collection/celeste
                if(hasSpecificLore(stack, "Évènement de collection") || hasSpecificLore(stack, "Commun"))
                {
                    lines.add(Text.translatable("zenprice.priceNotFixed"));
                }
                // Mettre un prix si c'est DES items
                else if(stack.getCount() > 1)
                {
                    lines.add(Text.of((config.priceText + " " + itemPrice + "$" + " (" + String.format("%.2f", (stack.getCount() * itemPrice)) + "$)")));
                }
                // Mettre un prix si c'est UN item
                else
                {
                    lines.add(Text.of((config.priceText + " " + itemPrice + "$")));
                }
            }
            else
            {
                lines.add(Text.translatable("zenprice.priceNotFound"));
            }
    }

    private double getEnchantementsPrice(ItemStack stack)
    {
        double itemPrice = 0;
        String[] enchantmentsArray = getEnchantmentsArray(stack);
        // Si il a un enchantement
        for (String enchant : enchantmentsArray)
        {
            if(itemPrices.get(enchant.toUpperCase()) != null)
            {
                itemPrice += itemPrices.get(enchant.toUpperCase());
            }
            else
            {
                LOGGER.error("Enchantement : " + enchant.toUpperCase() + " pas trouver !");
            }
        }

        return (itemPrice);
    }


    public static String[] getEnchantmentsArray(ItemStack stack)
    {
        ItemEnchantmentsComponent enchantments;

        if (stack.getItem() == Items.ENCHANTED_BOOK) {
            enchantments = EnchantmentHelper.getEnchantments(stack);
        }
        else if(stack.hasEnchantments())
        {
            enchantments = stack.getEnchantments();
        }
        else
        {
            return new String[0];
        }

        List<String> enchantmentsList = new ArrayList<>();
        for (RegistryEntry<Enchantment> entry : enchantments.getEnchantments()) {
            int level = enchantments.getLevel(entry);
            String enchantmentString = (entry.getIdAsString().substring(10) + "_" + level);
            enchantmentsList.add(enchantmentString);
        }
        return enchantmentsList.toArray(new String[0]);
    }

    public static boolean isConnectedToServer(String server) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
            if (networkHandler != null) {
                ServerInfo currentServer = client.getCurrentServerEntry();
                if (currentServer != null) {
                    if (server == null)
                    {
                        return false;
                    }
                    else
                    {
                        String serverAddress = currentServer.address;
                        return serverAddress.contains(server);
                    }
                }
            }
        }
        return false;
    }

    private void setChestName(MinecraftClient client)
    {
        // Si c'est un coffre
        if (client.player != null && client.currentScreen instanceof GenericContainerScreen) {
            ScreenAccessor screen = (ScreenAccessor) client.currentScreen;
            GenericContainerScreen containerScreen = (GenericContainerScreen) client.currentScreen;

            String modifiedText = "?";

            String originalText = containerScreen.getTitle().getString();

            // Verifier si c'est bien un coffre (pour eviter de faire bugger le /metier)
            // A CHANGER (je sais c'est de la merde ce que j'ai fais)
            if((originalText.toLowerCase().contains("chest") || (originalText.toLowerCase().contains("coffre") || (originalText.toLowerCase().contains("tonneau") || (originalText.toLowerCase().contains("barrel"))))))
            {
                String add = " " + config.chestPriceText +  " ";

                double chestPrice = getContainerMenuTotal(containerScreen.getScreenHandler());

                // Faire un texte pour eviter de faire une boucle et un texte mega long
                modifiedText = originalText.contains(add)
                        ? originalText.substring(0, originalText.indexOf(add)) + add + String.format("%.2f", chestPrice) + "$"
                        : originalText + add + String.format("%.2f", chestPrice) + "$";

                screen.zencraftprice$setTitle(Text.of(modifiedText));
            }
        }
        // Si c'est une shulker
        else if(client.player != null && client.currentScreen instanceof ShulkerBoxScreen)
        {
            ScreenAccessor screen = (ScreenAccessor) client.currentScreen;
            ShulkerBoxScreen shulkerScreen = (ShulkerBoxScreen) client.currentScreen;

            String modifiedText = "?";

            String originalText = shulkerScreen.getTitle().getString();
            String add = " " + config.chestPriceText +  " ";

            double chestPrice = getContainerMenuTotal(shulkerScreen.getScreenHandler());

            // Faire un texte pour eviter de faire une boucle et un texte mega long
            modifiedText = originalText.contains(add)
                    ? originalText.substring(0, originalText.indexOf(add)) + add + String.format("%.2f", chestPrice) + "$"
                    : originalText + add + String.format("%.2f", chestPrice) + "$";

            screen.zencraftprice$setTitle(Text.of(modifiedText));
        }
    }


    // Savoir le prix total dans un container
    // "Oui c'est pas comme ca qu'on fait" : tg
    private double getContainerMenuTotal(ScreenHandler screen) {
        double total = 0.0;
        double itemPrice = 0;

        // Iterate through the slots in the GenericContainerScreen
        for (Slot slot : screen.slots) {
            int menuSize = (screen.slots.size()) - 36;
            if (slot.hasStack() && slot.id < menuSize) {
                ItemStack itemStack = slot.getStack();

                // Check si c'est pas un item celeste
                if (!hasSpecificLore(itemStack, "Évènement de collection") || hasSpecificLore(itemStack, "Commun")) {
                    // Verifier si l'item est une shulker
                    if (itemStack.getItem() instanceof BlockItem)
                    {
                        BlockItem blockItem = (BlockItem) itemStack.getItem();
                        if (blockItem.toString().contains("shulker_box"))
                        {
                            totalPrice = getShulkerTotal(itemStack);
                            if (totalPrice > 0)
                            {
                                itemPrice = totalPrice;
                            }
                            else
                            {
                                if(itemPrices.get(toUpperCase(itemStack.getItem().toString().substring(10))) == null)
                                {
                                    LOGGER.error("2Item : " + toUpperCase(itemStack.getItem().toString().substring(10)) + " pas trouver dans la liste !");
                                }
                                else
                                {
                                    itemPrice = itemPrices.get(toUpperCase(itemStack.getItem().toString().substring(10)));
                                    itemPrice += getEnchantementsPrice(itemStack);
                                }
                            }
                        }
                        else
                        {
                            if(itemPrices.get(toUpperCase(itemStack.getItem().toString().substring(10))) == null)
                            {
                                LOGGER.error("3Item : " + itemStack.getItem().toString().substring(10) + " pas trouver dans la liste !");
                            }
                            else
                            {
                                itemPrice = itemPrices.get(toUpperCase(itemStack.getItem().toString().substring(10)));
                                itemPrice += getEnchantementsPrice(itemStack);
                            }
                        }
                    }
                    else
                    {
                        if(itemPrices.get(toUpperCase(itemStack.getItem().toString().substring(10))) == null)
                        {
                            LOGGER.error("4Item : " + itemStack.getItem().toString().substring(10) + " pas trouver dans la liste !");
                        }
                        else
                        {
                            itemPrice = itemPrices.get(toUpperCase(itemStack.getItem().toString().substring(10)));
                            itemPrice += getEnchantementsPrice(itemStack);
                        }
                    }
                    total += itemPrice * itemStack.getCount();
                }
            }
        }
        return total;
    }


    // Savoir le prix total d'une shulker dictement en forme item
    private double getShulkerTotal(ItemStack stack)
    {
        double total = 0;

        if (stack.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT) != null) {
            ContainerComponent container = stack.getOrDefault(DataComponentTypes.CONTAINER, ContainerComponent.DEFAULT);
            List<ItemStack> list = container.stream().toList();
            for (int i = 0; i < list.size(); i++)
            {
                ItemStack itemStack = list.get(i);
                if (!hasSpecificLore(itemStack, "Évènement de collection") || hasSpecificLore(stack, "Commun"))
                {
                    if ((itemPrices.get(toUpperCase(itemStack.getItem().toString().substring(10)))) == null)
                    {
                        LOGGER.error("5Item : " + itemStack.getItem().toString().substring(10) + " pas trouver dans la liste !");
                    }
                    else
                    {
                        total += ((itemPrices.get(toUpperCase(itemStack.getItem().toString().substring(10)))) + getEnchantementsPrice(itemStack)) * itemStack.getCount();
                    }
                }
            }
        }
        return total;
    }

    public static boolean hasSpecificLore(ItemStack stack, String targetLore) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if(lore != null)
        {
            List<Text> lines = lore.lines();
            for (int i = 0; i < lines.size(); ++i) {
                String s = lines.get(i).getString();
                if (s.contains(targetLore)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String GITHUB_EXCEL_RAW_URL = "https://raw.githubusercontent.com/Matyfou/ZenpriceTDP/main/table_des_prix.ods";
    private String GITHUB_API_URL = "https://api.github.com/repos/Matyfou/ZenpriceTDP/contents/table_des_prix.ods";

    private void checkAndUpdateExcelFile() {
        GITHUB_EXCEL_RAW_URL = config.odsFileDownloadLink_GITHUB_EXCEL_RAW_URL;
        GITHUB_API_URL = config.odsFileDownloadLink_GITHUB_API_URL;
        try {
            // Vérifier si le dossier de configuration existe
            Path configDirPath = Paths.get(CONFIG_DIR);
            if (!Files.exists(configDirPath)) {
                Files.createDirectories(configDirPath);
            }

            // Créer l'URL de l'API GitHub pour obtenir les métadonnées du fichier
            URL githubApiUrl = new URL(GITHUB_API_URL);
            HttpURLConnection connection = (HttpURLConnection) githubApiUrl.openConnection();
            connection.setRequestMethod("GET");

            // Vérifier si la réponse est un succès (code 200)
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Récupérer la date de dernière modification du fichier GitHub
                long githubLastModified = connection.getLastModified();

                // Récupérer la date de dernière modification du fichier local
                File localFile = new File(CONFIG_EXCEL_PATH);
                long localLastModified = localFile.lastModified();

                // Comparer les dates de modification
                if (githubLastModified > localLastModified || localFile == null) {
                    // Télécharger et remplacer le fichier local
                    LOGGER.info("Mise a jour du fichier Excel depuis GitHub...");

                    URL excelUrl = new URL(GITHUB_EXCEL_RAW_URL);
                    try (InputStream in = excelUrl.openStream();
                         OutputStream out = new FileOutputStream(CONFIG_EXCEL_PATH)) {
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }

                    LOGGER.info("Fichier Excel mis à jour avec succes.");
                } else {
                    LOGGER.info("Le fichier local est a jour.");
                }
            } else {
                LOGGER.error("La requete vers l'API GitHub a echouer avec le code : " + responseCode);
                ExcelFileGet();
            }

            connection.disconnect();

        } catch (IOException e) {
            LOGGER.error("Erreur lors de la mise a jour du fichier Excel depuis GitHub", e);
            ExcelFileGet();
        }
    }

    // Creer le fichier excel par default si il existe pas
    private void ExcelFileGet()
    {
        // Ensure config directory exists
        Path configDirPath = Paths.get(CONFIG_DIR);
        if (!Files.exists(configDirPath)) {
            LOGGER.error("Erreur pour creer le repertoire : " + CONFIG_DIR);
        }

        // Copy default Excel file if it doesn't exist
        Path configExcelPath = Paths.get(CONFIG_EXCEL_PATH);
        if (!Files.exists(configExcelPath)) {
            try (InputStream is = ZencraftPrice.class.getClassLoader().getResourceAsStream(DEFAULT_EXCEL_PATH)) {
                if (is == null) {
                    LOGGER.error("Default Excel file not found in resources: " + DEFAULT_EXCEL_PATH + " | " + ZencraftPrice.class.getClassLoader().getResourceAsStream(DEFAULT_EXCEL_PATH));
                    return;
                }
                Files.copy(is, configExcelPath);
                LOGGER.info("Fichier ODS par default copier a : " + CONFIG_EXCEL_PATH);
            } catch (IOException e) {
                LOGGER.error("Erreur pour copier le fichier ODS depuis le repertoire par default");
                return;
            }
        }
    }

    // Lis le fichier ODS pour le mettre dans la liste itemPrices
    private void loadPricesFromOds(String filePath) {
        try {
            long startTime = System.currentTimeMillis();
            Sheet sheet = new SpreadSheet(new File(filePath)).getSheet(0);

            int rowCount = sheet.getMaxRows();
            for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
                Range cellA = sheet.getRange(rowIndex, 0);
                Range cellB = sheet.getRange(rowIndex, 1);

                if (cellA != null && cellA.getValue() != null && cellA.getValue() instanceof String) {
                    String cellValueA = (String) cellA.getValue();

                    if (cellB != null && cellB.getValue() != null && cellB.getValue() instanceof Double) {
                        double cellValueB = (Double) cellB.getValue();
                        itemPrices.put(cellValueA.toUpperCase(), cellValueB);
                    }
                }
            }

            long fileLoadTime = System.currentTimeMillis();
            LOGGER.info("Fichier ODS charger avec succes en "  + (fileLoadTime - startTime) + " ms");
        } catch (IOException ex) {
            LOGGER.error("Erreur lors du chargement du fichier ODS", ex);
        }
    }
}
