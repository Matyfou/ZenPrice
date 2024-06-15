package matyfou.zencraftpriceviewer.zencraftprice;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;

import static com.google.common.base.Ascii.toUpperCase;


public class ZencraftPrice implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("zencraftprice");
    private final Map<String, Double> itemPrices = new HashMap<>();

    double totalPrice = 0;

    @Override
    public void onInitialize() {

        loadPricesFromExcel();

        ItemTooltipCallback.EVENT.register((stack, context, lines) -> {

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
                String.format("%.2f", itemPrice);
                // Ne pas mettre de prix si c'est un item de collection/celeste
                if(hasSpecificLore(stack, "Évènement de collection"))
                {
                    lines.add(Text.of(("Cet item n'a pas de prix fixe")));
                }
                // Mettre un prix si c'est DES items
                else if(stack.getCount() > 1)
                {
                    lines.add(Text.of(("Prix : " + itemPrice + "$" + " (" + (stack.getCount() * itemPrice) + "$)")));
                }
                // Mettre un prix si c'est UN item
                else
                {
                    lines.add(Text.of(("Prix : " + itemPrice + "$")));
                }
            }
            else
            {
                lines.add(Text.of("Prix non trouvé"));
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            getChestName(client);
        });
    }

    private void getChestName(MinecraftClient client)
    {
        if (client.player != null && client.currentScreen instanceof GenericContainerScreen) {
            ScreenAccessor screen = (ScreenAccessor) client.currentScreen;
            GenericContainerScreen containerScreen = (GenericContainerScreen) client.currentScreen;

            // Get the original title
            String originalText = containerScreen.getTitle().getString();
            String add = " | Prix : ";

            // Calculate the chest price
            double chestPrice = getChestTotal(containerScreen);

            // Construct the new title
            String modifiedText = originalText.contains(add)
                    ? originalText.substring(0, originalText.indexOf(add)) + add + String.format("%.2f", chestPrice) + "$"
                    : originalText + add + String.format("%.2f", chestPrice) + "$";

            // Set the new title
            screen.zencraftprice$setTitle(Text.of(modifiedText));
        }
    }

    private double getChestTotal(GenericContainerScreen containerScreen) {
        double total = 0.0;
        double itemPrice = 0;

        // Iterate through the slots in the GenericContainerScreen
        for (Slot slot : containerScreen.getScreenHandler().slots) {
            int menuSize = (containerScreen.getScreenHandler().slots.size()) - 36;
            if (slot.hasStack() && slot.id < menuSize) {
                ItemStack itemStack = slot.getStack();

                // Check if the itemStack does not have the specific lore
                if (!hasSpecificLore(itemStack, "Évènement de collection")) {
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
                        }
                    }
                    total += itemPrice * itemStack.getCount();
                }
            }
        }
        return total;
    }


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
                    if(!hasSpecificLore(itemStack, "Évènement de collection"))
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

    private void loadPricesFromExcel() {
        try {
            String filePath = "table_des_prix.xlsx";
            InputStream inputStream = ZencraftPrice.class.getClassLoader().getResourceAsStream(filePath);

            if (inputStream != null) {
                Workbook workbook = WorkbookFactory.create(inputStream);
                Sheet sheet = workbook.getSheetAt(0);

                for (Row row : sheet) {
                    Cell cellA = row.getCell(0); // Colonne A
                    Cell cellB = row.getCell(1); // Colonne B

                    if (cellA != null && cellA.getCellType() == CellType.STRING) {
                        String cellValueA = cellA.getStringCellValue();

                        if (cellB != null && cellB.getCellType() == CellType.NUMERIC) {
                            double cellValueB = cellB.getNumericCellValue();
                            itemPrices.put(cellValueA, cellValueB);
                        }
                    }
                }
                inputStream.close();
                LOGGER.info("Fichier Excel chargé avec succès.");
            } else {
                LOGGER.error("Le fichier Excel n'a pas pu être chargé.");
            }
        } catch (IOException | EncryptedDocumentException ex) {
            LOGGER.error("Erreur lors du chargement du fichier Excel", ex);
        }
    }
}
