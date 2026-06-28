package dev.auction.auctionhouse.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

public class ItemSerializer {

    public static String toBase64(ItemStack item) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream boos = new BukkitObjectOutputStream(baos)) {
            boos.writeObject(item);
        }
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    public static ItemStack fromBase64(String data) {
        if (data == null || data.isEmpty()) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(data);
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            try (BukkitObjectInputStream bois = new BukkitObjectInputStream(bais)) {
                return (ItemStack) bois.readObject();
            }
        } catch (Exception e) {
            return null;
        }
    }
}
