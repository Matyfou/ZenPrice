package matyfou.zencraftpriceviewer.zencraftprice;

import com.github.miachm.sods.Range;
import com.github.miachm.sods.Sheet;
import com.github.miachm.sods.SpreadSheet;
import matyfou.zencraftpriceviewer.MyConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ServerInfo;
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

    public static final MyConfig CONFIG = MyConfig.createAndLoad();

    private String CONFIG_DIR = (FabricLoader.getInstance().getConfigDir() + "\\Zenprice");
    private static final String DEFAULT_EXCEL_PATH = "table_des_prix.ods";
    private String CONFIG_EXCEL_PATH = CONFIG_DIR + "\\table_des_prix.ods";

    double totalPrice = 0;

    @Override
    public void onInitialize()
    {
        if(CONFIG.excelAutoUpdate())
        {
            checkAndUpdateExcelFile();
        }
        else
        {
            ExcelFileGet();
        }

        loadPricesFromOds(CONFIG_EXCEL_PATH);

        ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
            putTitles(stack, lines);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            setChestName(client);
        });
    }

    private static final String GITHUB_EXCEL_RAW_URL = CONFIG.odsFileDownloadLink_GITHUB_EXCEL_RAW_URL();
    private static final String GITHUB_API_URL = CONFIG.odsFileDownloadLink_GITHUB_API_URL();

    private void checkAndUpdateExcelFile() {
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
                    LOGGER.info("Mise à jour du fichier Excel depuis GitHub...");

                    URL excelUrl = new URL(GITHUB_EXCEL_RAW_URL);
                    try (InputStream in = excelUrl.openStream();
                         OutputStream out = new FileOutputStream(CONFIG_EXCEL_PATH)) {
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }

                    LOGGER.info("Fichier Excel mis à jour avec succès.");
                } else {
                    LOGGER.info("Le fichier local est à jour.");
                }
            } else {
                LOGGER.error("La requête vers l'API GitHub a échoué avec le code : " + responseCode);
                ExcelFileGet();
            }

            connection.disconnect();

        } catch (IOException e) {
            LOGGER.error("Erreur lors de la mise à jour du fichier Excel depuis GitHub", e);
            ExcelFileGet();
        }
    }

    private void putTitles(ItemStack stack, List<Text> lines)
    {
        if(!CONFIG.serverOnlyOption() || (CONFIG.serverOnlyOption() && isConnectedToServer("play.zencraft.fr")))
        {
            String itemName = toUpperCase(stack.getItem().toString());
            double itemPrice = 0;
            totalPrice = 0;

            if(itemPrices.get(itemName) == null)
            {
                LOGGER.error("Item : " + itemName + " pas trouver dans la liste !");
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
                    lines.add(Text.of((CONFIG.priceText() + " " + itemPrice + "$" + " (" + String.format("%.2f", (stack.getCount() * itemPrice)) + "$)")));
                }
                // Mettre un prix si c'est UN item
                else
                {
                    lines.add(Text.of((CONFIG.priceText() + " " + itemPrice + "$")));
                }
            }
            else
            {
                lines.add(Text.translatable("zenprice.priceNotFound"));
            }
        }
    }

    private double getEnchantementsPrice(ItemStack stack)
    {
        double itemPrice = 0;
        // Si il a un enchantement
        if(!EnchantmentHelper.get(stack).isEmpty())
        {
            // Récupérer les enchantements des livres enchantés
            if (stack.getItem() == Items.ENCHANTED_BOOK) {
                NbtList enchantments = EnchantedBookItem.getEnchantmentNbt(stack);
                for (NbtElement enchantmentElement : enchantments) {
                    if (enchantmentElement instanceof NbtCompound) {
                        NbtCompound enchantmentCompound = (NbtCompound) enchantmentElement;
                        Identifier id = Identifier.tryParse(enchantmentCompound.getString("id"));
                        int level = enchantmentCompound.getInt("lvl");
                        if (id != null) {
                            //enchantmentsList.add(id.getPath() + "_" + level);
                            if(itemPrices.get((id.getPath().toUpperCase() + "_" + level)) != null)
                            {
                                itemPrice += itemPrices.get((id.getPath().toUpperCase() + "_" + level));
                            }
                        }
                    }
                }
                itemPrice -= 225.86;
            }
            for (String enchantment : getEnchantmentsArray(stack))
            {
                if(!(enchantment == null)) {
                    if(itemPrices.get(enchantment.toUpperCase()) != null)
                    {
                        itemPrice += itemPrices.get(enchantment.toUpperCase());
                    }
                    else
                    {
                        LOGGER.error("L'enchantement : " + enchantment + " pas trouver !");
                    }
                }
            }
        }
        return (itemPrice);
    }

    public static String[] getEnchantmentsArray(ItemStack itemStack) {
        // Vérifier si l'item a des enchantements
        if (!itemStack.hasEnchantments()) {
            return new String[0];
        }

        // Récupérer les enchantements de l'item
        Map<Enchantment, Integer> enchantments = EnchantmentHelper.get(itemStack);

        // Construire la liste des enchantements
        List<String> enchantmentsList = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            String enchantmentString = getEnchantmentId(entry.getKey()) + "_" + entry.getValue();
            enchantmentsList.add(enchantmentString);
        }

        // Convertir la liste en tableau
        return enchantmentsList.toArray(new String[0]);
    }

    private static String getEnchantmentId(Enchantment enchantment) {
        Identifier id = Registries.ENCHANTMENT.getId(enchantment);
        return id == null ? "unknown" : id.getPath();
    }

    public static boolean isConnectedToServer(String server) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
            if (networkHandler != null) {
                ServerInfo currentServer = client.getCurrentServerEntry();
                if (currentServer != null) {
                    String serverAddress = currentServer.address;
                    return server.equals(serverAddress);
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
                String add = " " + CONFIG.chestPriceText() +  " ";

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
            String add = " " + CONFIG.chestPriceText() +  " ";

            double chestPrice = getContainerMenuTotal(shulkerScreen.getScreenHandler());

            // Faire un texte pour eviter de faire une boucle et un texte mega long
            modifiedText = originalText.contains(add)
                    ? originalText.substring(0, originalText.indexOf(add)) + add + String.format("%.2f", chestPrice) + "$"
                    : originalText + add + String.format("%.2f", chestPrice) + "$";

            screen.zencraftprice$setTitle(Text.of(modifiedText));
        }
    }


    // Savoir le prix total dans un container
    // "Oui c'est pas comme ca qu'on fais" : tg
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
                        if (blockItem.getBlock() instanceof ShulkerBoxBlock)
                        {
                            totalPrice = getShulkerTotal(itemStack);
                            if (totalPrice > 0)
                            {
                                itemPrice = totalPrice;
                            }
                            else
                            {
                                if(itemPrices.get(toUpperCase(itemStack.getItem().toString())) == null)
                                {
                                    LOGGER.error("Item : " + itemStack.getItem().toString() + " pas trouver dans la liste !");
                                }
                                else
                                {
                                    itemPrice = itemPrices.get(toUpperCase(itemStack.getItem().toString()));
                                    itemPrice += getEnchantementsPrice(itemStack);
                                }
                            }
                        }
                        else
                        {
                            if(itemPrices.get(toUpperCase(itemStack.getItem().toString())) == null)
                            {
                                LOGGER.error("Item : " + itemStack.getItem().toString() + " pas trouver dans la liste !");
                            }
                            else
                            {
                                itemPrice = itemPrices.get(toUpperCase(itemStack.getItem().toString()));
                                itemPrice += getEnchantementsPrice(itemStack);
                            }
                        }
                    }
                    else
                    {
                        if(itemPrices.get(toUpperCase(itemStack.getItem().toString())) == null)
                        {
                            LOGGER.error("Item : " + itemStack.getItem().toString() + " pas trouver dans la liste !");
                        }
                        else
                        {
                            itemPrice = itemPrices.get(toUpperCase(itemStack.getItem().toString()));
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
        NbtCompound blockEntityTag = stack.getSubNbt("BlockEntityTag");
        if (blockEntityTag != null) {
            NbtList items = blockEntityTag.getList("Items", 10);
            DefaultedList<ItemStack> inventory = DefaultedList.ofSize(27, ItemStack.EMPTY);
            for (int i = 0; i < items.size(); i++) {
                NbtCompound itemTag = items.getCompound(i);
                int slot = itemTag.getByte("Slot") & 255;
                if (slot >= 0 && slot < inventory.size()) {
                    ItemStack itemStack = ItemStack.fromNbt(itemTag);
                    if(!hasSpecificLore(itemStack, "Évènement de collection") || hasSpecificLore(stack, "Commun"))
                    {
                        if((itemPrices.get(toUpperCase(itemStack.getItem().toString()))) == null)
                        {
                            LOGGER.error("Item : " + itemStack.getItem().toString() + " pas trouver dans la liste !");
                        }
                        else
                        {
                            total += (itemPrices.get(toUpperCase(itemStack.getItem().toString()))) * itemStack.getCount();
                        }
                    }
                    inventory.set(slot, itemStack);
                }
            }
        }
        return total;
    }


    private static boolean hasSpecificLore(ItemStack stack, String targetLore) {
        if (stack.hasNbt()) {
            NbtCompound display = stack.getSubNbt("display");
            if (display != null) {
                NbtList lore = display.getList("Lore", NbtString.STRING_TYPE);
                for (int i = 0; i < lore.size(); i++) {
                    NbtString loreEntry = NbtString.of(lore.getString(i));
                    if (loreEntry.asString().contains(targetLore)) {
                        return true;
                    }
                }
            }
        }
        return false;
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
            LOGGER.info("Fichier ODS chargé avec succes en "  + (fileLoadTime - startTime) + " ms");
        } catch (IOException ex) {
            LOGGER.error("Erreur lors du chargement du fichier ODS", ex);
        }
    }
}
