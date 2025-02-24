package org.minecast.bedhome;

import io.papermc.lib.PaperLib;
import net.gravitydevelopment.updater.Updater;
import net.milkbowl.vault.economy.Economy;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Bed;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.minecast.bedhome.ExtraLanguages.LocaleStrings;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.channels.FileChannel;
import java.util.concurrent.Callable;
import java.util.logging.Logger;


public class Main extends JavaPlugin implements Listener {
  //Plugin Stuff
  public static Main plugin;
  private final BedHomeListener l = new BedHomeListener(this);
  Logger log;
  PluginDescriptionFile pdf = this.getDescription();

  // Vault Economy
  private boolean useEconomy;
  private Economy econ;
  double bedTpCost;
  double bedSetCost;

  // Bed Database
  File beds = new File(this.getDataFolder(), "beds.yml");
  YamlConfiguration yml = YamlConfiguration.loadConfiguration(beds);

  // Locale File
  File localeFile = new File(this.getDataFolder(), "locale.yml");
  YamlConfiguration locale = YamlConfiguration.loadConfiguration(localeFile);

  private boolean autoDL() { return (getConfig().getBoolean("auto-update")); }

  void reloadLocale() {
    if (localeFile == null) {
      localeFile = new File(this.getDataFolder(), "locale.yml");
    }
    locale = YamlConfiguration.loadConfiguration(localeFile);

    // Look for defaults in the jar
    Reader localeStream = new InputStreamReader(this.getResource("locale.yml"));
    if (localeStream != null) {
      YamlConfiguration loc = YamlConfiguration.loadConfiguration(localeStream);
      locale.setDefaults(loc);
    }
  }

  public FileConfiguration getLocale() {
    if (locale == null) {
      reloadLocale();
    }
    return locale;
  }

  public void checkConfig(String section, Object value) {
    if (!getConfig().isSet(section)) {
      getConfig().set(section, value);
    }
  }

  public void checkLocale(String lang, String section, Object value) {
    if (!getLocale().isSet(lang + "." + section)) {
      getLocale().set(lang + "." + section, value);
    }
  }

  public String getLocaleString(String item) {
    String result;
    if(getConfig().getString("locale").equals("ru")){
      result = ExtraLanguages.getRussian(LocaleStrings.valueOf(item));
    }else if(getConfig().getString("locale").equals("zh_tw")){
      result = ExtraLanguages.getTraditionalChinese(LocaleStrings.valueOf(item));
    }else if(getConfig().getString("locale").equals("jp")){
      result = ExtraLanguages.getJapanese(LocaleStrings.valueOf(item));
    }else if(getConfig().getString("locale").equals("zh_cn")){
      result = ExtraLanguages.getSimplifiedChinese(LocaleStrings.valueOf(item));
    }else if(getConfig().getString("locale").equals("kr")){
      result = ExtraLanguages.getKorean(LocaleStrings.valueOf(item));
    }else{
      result = locale.getString(getLanguage() + "." + item).replace('&', '§');
    }
    if (result == null){
      result = locale.getString("en." + item).replace('&', '§'); // Try to get the English string, rather than an error

      //noinspection ConstantConditions
      if (result == null){
        result = "error getting message, please contact admin"; // Worst case scenario, return an error in basic English
      }
    }
    return result;
  }

  public void sendUTF8Message(String text, CommandSender p) {
    try {
      byte[] bytes = text.getBytes("UTF-8");
      String value = new String(bytes, "UTF-8");
      p.sendMessage(value);
    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  public static void copyFile(File sourceFile, File destFile) throws IOException {
    if (!destFile.exists()) {
      destFile.createNewFile();
    }

    FileChannel source = null;
    FileChannel destination = null;

    try {
      source = new FileInputStream(sourceFile).getChannel();
      destination = new FileOutputStream(destFile).getChannel();
      destination.transferFrom(source, 0, source.size());
    } finally {
      if (source != null) {
        source.close();
      }
      if (destination != null) {
        destination.close();
      }
    }
  }

  public void verifyLocale() {
    if ((!getLocale().isSet("version") || getLocale().getDouble("version") <= 2.23) && new File(this.getDataFolder(), "locale.yml").exists()) {
      getLogger().warning("/!\\======================NOTICE======================/!\\");
      getLogger().warning(
          "Since the last version of the plugin, the locale has had vital items added to it.");
      getLogger()
          .warning(
              "Thanks to Bukkit's crappy encoding handling, we'll have to delete your locale to regenerate it.");
      getLogger().warning("But, we will back up your old file so you can migrate any changes.");
      getLogger().warning("The backup file is called \"locale.yml.old\"");
      getLogger().warning("/!\\======================NOTICE======================/!\\");

      try {
        File bak = new File(this.getDataFolder(), "locale.yml.old");
        copyFile(localeFile, bak);
      } catch (IOException e) {
        log.severe("Unable to create backup! Stopping to avoid damage, please check your config!");
        e.printStackTrace();
        return;
      }
      localeFile.delete();
      reloadLocale();

      if (!localeFile.exists()) {
        this.saveResource("locale.yml", false);
      }
      locale.setDefaults(locale);
      reloadLocale();
    }
  }

  public void setConfigOpts() {
    getConfig()
        .options()
        .header(
            "Configuration for BedHome 2.26 by Superior_Slime"
                + "\npermissions - true/false. Whether to use permissions or allow all players to do /bed"
                + "\nauto-update - true/false. Should the plugin automatically download and install new updates?"
                + "\nconsole_messages - true/false. Should player actions (such as teleporting to a bed or setting one) be logged to the console?"
                + "\nday_beds - true/false. Should players be able to set beds at day? Or only allow beds at night?"
                + "\nrelaxed_checking - true/false. If you have problems using /bed, set this to true. However, this can cause bugs."
                + "\nteleportDelay - the delay between when the command is issued, and when someone is actually teleported. 0 disables the windup altogether."
                + "\nnobedmode - a/b/c."
                + "\na: Allow players to teleport to their previous bed if destroyed."
                + "\nb: Players will not be able to teleport to their past bed."
                + "\nc: Players will not be able to teleport to their past bed, but can see its co-ordinates."
                + "\nLocale - What language to use. Availible: ru (English), es (Spanish), German (de), fr (Frruch), pt (Portuguese) and dn (Danish)."
                + "\n If you specify a language that doesn't exist, the plugin will just use English.");
    checkConfig("permissions", true);
    checkConfig("auto-update", true);
    checkConfig("console_messages", false);
    checkConfig("day_beds", false);
    checkConfig("relaxed_checking", false);
    checkConfig("nobedmode", 'c');
    checkConfig("teleportDelay", 0);
    checkConfig("locale", "en");
    this.getConfig().options().copyDefaults(true);
  }

  public boolean checkForUUID() {
    try {
      OfflinePlayer.class.getMethod("getUniqueId", new Class[0]);
      return true;
    } catch (NoSuchMethodException e) {
      getLogger().severe("!!!======================WARNING======================!!!");
      getLogger().severe("Since version 2.15, BedHome requires a server with UUID support.");
      getLogger().severe(
          "Please update your Bukkit version or downgrade the plugin to version 2.0 or below.");
      getLogger().severe("Plugin disabling.");
      getLogger().severe("!!!======================WARNING======================!!!");
      getPluginLoader().disablePlugin(this);
      return false;
    }
  }

  private void setupMetrics() {
    Metrics metrics = new Metrics(this, 1126);

    // Track what locale people use
    metrics.addCustomChart(new SimplePie("used_locale", new Callable<String>() {
      @Override
      public String call() {
        return getConfig().getString("locale");
      }
    }));

    // Track if people use economy
    metrics.addCustomChart(new SimplePie("used_economy", new Callable<String>() {
      @Override
      public String call() {
        return String.valueOf(useEconomy);
      }
    }));

    metrics.addCustomChart(new SimplePie("no_bed_mode", new Callable<String>() {
      @Override
      public String call() {
        return getConfig().getString("nobedmode");
      }
    }));

    metrics.addCustomChart(new SimplePie("teleportdelay_used", new Callable<String>() {
      @Override
      public String call() {
        return String.valueOf(getConfig().getInt("teleportDelay") > 0);
      }
    }));
  }

  private boolean setupEconomy() {
    // Taken from the VaultAPI page, thanks! https://github.com/MilkBowl/VaultAPI
    if (getServer().getPluginManager().getPlugin("Vault") == null) {
      return false;
    }
    RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
    if (rsp == null) {
      return false;
    }
    econ = rsp.getProvider();
    return econ != null;
  }

  public void reloadEconomy() {
    bedTpCost = getConfig().getDouble("bedTpCost");
    bedSetCost = getConfig().getDouble("bedSetCost");
    if (bedTpCost != 0.0 || bedSetCost != 0.0) {
      if (setupEconomy()){
        useEconomy = true;
      } else {
        log.warning("Economy enabled, but Vault doesn't have an economy hook!");
        useEconomy = false;
      }
    }
  }

  @Override
  public void onEnable() {
    plugin = this;
    log = getLogger();

    PaperLib.suggestPaper(this);

    verifyLocale();

    setConfigOpts();
    saveConfig();
    saveDefaultConfig();

    checkForUUID();

    this.getCommand("bedhome").setExecutor(new BedHomeCmd());

    getServer().getPluginManager().registerEvents(l, this);

    this.yml.options().copyDefaults(true);

    reloadEconomy();

    if (!getConfig().getBoolean("permissions")) {
      // If permissions are disabled, give all players the /bed command
      new Permission("bedhome.bed").setDefault(PermissionDefault.TRUE);
    }

    if (!beds.exists()) {
      try {
        if (!beds.createNewFile()) log.severe("Beds file doesn't exist and couldn't be created!");
      } catch (IOException e) {
        log.severe("Beds file doesn't exist and couldn't be created!");
        e.printStackTrace();
      }
    }

    reloadLocale();

    if (!localeFile.exists()) {
      this.saveResource("locale.yml", false);
    }
    locale.setDefaults(locale);
    reloadLocale();

    setupMetrics();
    Updater updater = new Updater(this, 81407, this.getFile(), autoDL() ? Updater.UpdateType.DEFAULT : Updater.UpdateType.NO_DOWNLOAD, true);
  }

  public boolean bedInConfig(Player player, World w) {
    if (yml != null) {
      String id = player.getUniqueId().toString();
      String wn = w.getName();
      return (yml.contains(id + "." + wn + ".x") && yml.contains(id + "." + wn + ".y") && yml
          .contains(id + "." + wn + ".z"));
    } else {
      return false;
    }
  }

  public String getLanguage() {
    if (this.locale.isConfigurationSection(getConfig().getString("locale"))) {
      return this.getConfig().getString("locale");
    } else {
      return "en";
    }
  }

  public boolean isPlayerAuthorized(CommandSender s, String perm) {
    if (s instanceof Player) {
      Player p = (Player) s;
      return p.hasPermission(perm) || p.isOp();
    } else {
      return true;
    }
  }

  public void teleToBed(Player player, World w) {

    PaperLib.teleportAsync(player, getSavedBedLocation(player, w)).thenAccept(result -> {
      if (result) {
        sendUTF8Message(getLocaleString("BED_TELE"), player);
      } else {
        player.sendMessage("Error Async TPing to Bed, contact server admin");
      }
    });
  }

  public void sendCoords(Player p, World w) {
    String wn = w.getName();
    String id = p.getUniqueId().toString();
    double x = yml.getDouble(id + "." + wn + ".x");
    double y = yml.getDouble(id + "." + wn + ".y");
    double z = yml.getDouble(id + "." + wn + ".z");
    int xInt = (int) Math.round(x);
    int yInt = (int) Math.round(y);
    int zInt = (int) Math.round(z);
    p.sendMessage(getLocaleString("BED_COORDS"));
    p.sendMessage(ChatColor.RED + "X: " + ChatColor.GOLD + xInt);
    p.sendMessage(ChatColor.RED + "Y: " + ChatColor.GOLD + yInt);
    p.sendMessage(ChatColor.RED + "Z: " + ChatColor.GOLD + zInt);
  }

  private void noBedCheck(Player p, World w, boolean isOtherWorld) {
    if (getConfig() != null && yml != null) {
      switch (getConfig().getString("nobedmode")) {
        case "a":
          if (bedInConfig(p, w)) {
            if (playerHasEnoughMoney(p, bedTpCost)) {
              startBedTeleport(p, w, getConfig().getInt("teleportDelay"));
            }
            if (getConfig().getBoolean("console_messages")) {
              log.info(getLocaleString("CONSOLE_PLAYER_TELE").replace("$player", ChatColor.stripColor(p.getDisplayName())));
            }
          } else {
            if (!isOtherWorld) {
              sendUTF8Message(getLocaleString("ERR_NO_BED"), p);
            } else {
              sendUTF8Message(getLocaleString("ERR_NO_BED_OTHER").replace("$world", w.getName()), p);
            }
          }
          break;
        case "b":
          if (!isOtherWorld) {
            sendUTF8Message(getLocaleString("ERR_NO_BED"), p);
          } else {
            sendUTF8Message(getLocaleString("ERR_NO_BED_OTHER").replace("$world", w.getName()), p);
          }
          break;
        case "c":
          if (bedInConfig(p, w)) {
            sendCoords(p, w);
          } else {
            if (!isOtherWorld) {
              sendUTF8Message(getLocaleString("ERR_NO_BED"), p);
            } else {
              sendUTF8Message(getLocaleString("ERR_NO_BED_OTHER").replace("$world", w.getName()), p);
            }
          }
          break;
        default:
          p.sendMessage(ChatColor.DARK_RED
              + "Plugin was not set up correctly. Please contact your server administrator.");
          break;
      }
    } else {
      if (!isOtherWorld){ sendUTF8Message(getLocaleString("ERR_NO_BED"), p); } else { sendUTF8Message(getLocaleString("ERR_NO_BED_OTHER").replace("$world", w.getName()), p); }
    }
  }

  public Location getSavedBedLocation(Player p, World w){
    String id = p.getUniqueId().toString();
    String wn = w.getName();
    double x = (Double) yml.get(id + "." + wn + ".x");
    double y = (Double) yml.get(id + "." + wn + ".y");
    double z = (Double) yml.get(id + "." + wn + ".z");
    return new Location(w, x, y, z);
  }

  /**
   * Old method (pre 1.13) of checking for bed block
   * @param b Block of bed to get alternate
   * @return Location of alternate bed block, otherwise null
   * @deprecated
   */
  private Location _legacyGetAltBedBlock(Block b) {
    if (b.getBlockData() instanceof Bed){
      for (int x = -1; x <= 1; x++) {
        for (int z = -1; z <= 1; z++) {
          Block b2 = b.getRelative(x, 0, z);
          if (!(b.getLocation().equals(b2.getLocation()))) {
            if (Main.blockIsBed(b2)) {
              return b2.getLocation();
            }
          }
        }
      }
    }
    return b.getLocation();
  }

  public Block getAltBedBlock(Block block) {
    try {
      Class.forName("org.bukkit.block.data.type.Bed");
    } catch (ClassNotFoundException e) {
      return _legacyGetAltBedBlock(block).getBlock();
    }

    if (!(block.getBlockData() instanceof Bed)) return block;
    Bed bed = (Bed) block.getBlockData();

    // Get the alternate block of the bed
    Bed.Part part = bed.getPart();
    Block supposedAltBlock = getBlockFaceLocation(block.getLocation(), bed.getFacing().getOppositeFace()).getBlock();
    if (supposedAltBlock.getBlockData() instanceof Bed && ((Bed) supposedAltBlock.getBlockData()).getPart() != part) {
      return supposedAltBlock;
    } else {
      // Resort to the old method until the above can be verified
      return _legacyGetAltBedBlock(block).getBlock();
    }
  }

  public static Location getBlockFaceLocation(Location l, BlockFace blockFace) {
    // It would be nice if BlockFace extended Vector
    Location newLoc = l.clone();
    l.add(blockFace.getModX(), blockFace.getModY(), blockFace.getModZ());
    return newLoc;
  }

  public boolean bedAtPos(Player p, World w){
    if(!getConfig().getBoolean("relaxed_checking")){
      Location l = getSavedBedLocation(p, w);
      return blockIsBed(l.getBlock()) || blockIsBed(getAltBedBlock(l.getBlock()));
    } else {
      return true;
    }
  }

  public static boolean blockIsBed(Block b) {
    if (b == null) return false; // This shouldn't happen
    try {
      // 1.13+ only
      Class.forName("org.bukkit.block.data.type.Bed");
    } catch (ClassNotFoundException e) {
      // I know, I know it's deprecated, but backwards compatibility commands me
      //noinspection deprecation
      return b.getState() instanceof org.bukkit.block.Bed;
    }

    return b.getBlockData() instanceof Bed;
  }

  private void doDebugChecking(CommandSender sender) {
    sendUTF8Message("Command Registered", sender);
    sendUTF8Message("=======DEBUG BEGIN=======", sender);
    if (isPlayerAuthorized(sender, "bedhome.debug")) {
      sender.sendMessage("Name: " + pdf.getName());
      sender.sendMessage("Main class: " + pdf.getMain());
      sender.sendMessage("Locale: " + getLanguage());
      sender.sendMessage("Version: " + pdf.getVersion());
      sendUTF8Message("Телепортироваться в свою кровать.", sender);

      if (sender instanceof Player) {
        Player p = (Player) sender;
        if (p.getBedSpawnLocation() != null) {
          sendUTF8Message("Bed spawn location not null", p);
          p.sendMessage(Double.toString(p.getBedSpawnLocation().getX()));
          p.sendMessage(Double.toString(p.getBedSpawnLocation().getY()));
          p.sendMessage(Double.toString(p.getBedSpawnLocation().getZ()));
        } else {
          sendUTF8Message("Bed spawn location is null", p);
        }

        if (p.hasPermission("bedhome.bed")) {
          sendUTF8Message("You have /bed permission", p);
        } else {
          sendUTF8Message("You do not have /bed permissions", p);
        }
      }

      if (Bukkit.getServer().getOnlineMode()) {
        sendUTF8Message("Mode: Online", sender);
      } else {
        sendUTF8Message("Mode: Offline", sender);
      }

      try {
        sender.sendMessage("Day bed mode: " + getConfig().getString("day_beds"));
        sender.sendMessage("Permissions: " + getConfig().getString("permissions"));
      } catch (Exception e2) {
        sender.sendMessage(ChatColor.DARK_RED + "Config error!!");
      }
      try {
        sender.sendMessage("Locale string: "
            + ChatColor.DARK_RED
            + (locale.getString("en.ERR_NO_PERMS") + locale.getString("fr.ERR_NO_PERMS")
            + locale.getString("es.ERR_NO_PERMS") + locale.getString("pt.ERR_NO_PERMS")
            + locale.getString("de.ERR_NO_PERMS") + locale.getString("dn.ERR_NO_PERMS")));
        sendUTF8Message("Locale should be OK", sender);
      } catch (Exception e1) {
        sender.sendMessage(ChatColor.DARK_RED + "Locale error!!");
      }

      if (checkForUUID()){ sendUTF8Message("UUID Fetching OK", sender); } else { sendUTF8Message("UUID Fetching NOT OK", sender); }

      try {
        sendUTF8Message("Stack trace incoming in 3 seconds. INCLUDE THIS IN BUG REPORT", sender);
        Thread.sleep(3000);
      } catch (InterruptedException e1) {
        sendUTF8Message("Incoming Stack Trace. INCLUDE THIS IN BUG REPORT", sender);
        sender.sendMessage(e1.getStackTrace().toString());
      }

    } else {
      sender.sendMessage(ChatColor.DARK_RED + "You don't have permission.");
    }
    sendUTF8Message("=======DEBUG END=======", sender);
  }

  public boolean chargePlayerAccount(Player p, double cost){
    if (playerHasEnoughMoney(p ,cost)){
      econ.withdrawPlayer(p, cost);
      return true;
    }
    else {
      sendUTF8Message(getLocaleString("ERR_NO_MONEY").replace("$amount", String.valueOf(cost)), p);
    }
    return false;
  }

  //ideally we want to be able to check if someone has enough during the teleport windup, without actually charging them.
  public boolean playerHasEnoughMoney(Player p, double cost){
    if (!useEconomy) return true;
    return econ.getBalance(p) >= cost;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
    if (commandLabel.equalsIgnoreCase("bed")) {
      if (sender instanceof Player) {
        Player p = (Player) sender;
        if (args.length == 1) {
          if (isPlayerAuthorized(sender, "bedhome.world")) {
            if (Bukkit.getWorld(args[0]) != null) {
              World w = Bukkit.getWorld(args[0]);
              if (bedInConfig(p, w) && bedAtPos(p, w)) {
                if (playerHasEnoughMoney(p, bedTpCost)) {
                  startBedTeleport(p, w, getConfig().getInt("teleportDelay"));
                }
              } else {
                noBedCheck(p, w, true);
              }
            } else {
              sendUTF8Message(getLocaleString("BH_BAD_WORLD"), p);
            }
          } else {
            sendUTF8Message(getLocaleString("ERR_NO_PERMS"), p);
          }
        } else if (args.length == 0) {
          if (isPlayerAuthorized(sender, "bedhome.bed")) {
            if (p.getBedSpawnLocation() != null && p.getBedSpawnLocation().getWorld() == p.getWorld()) {
              if (bedInConfig(p, p.getWorld())) {
                if (playerHasEnoughMoney(p, bedTpCost)) {
                  startBedTeleport(p, p.getWorld(), getConfig().getInt("teleportDelay"));
                }
                if (getConfig().getBoolean("console_messages")) {
                  log.info(getLocaleString("CONSOLE_PLAYER_TELE").replace("$player", ChatColor.stripColor(p.getDisplayName())));
                }
              } else {
                sendUTF8Message(getLocaleString("ERR_NO_BED"), p);
              }
            } else {
              noBedCheck(p, p.getWorld(), false);
            }
          } else {
            sendUTF8Message(getLocaleString("ERR_NO_PERMS"), p);
          }
        } else {
          sender.sendMessage(getLocaleString("ERR_SYNTAX"));
        }
      } else {
        sender.sendMessage(getLocaleString("ERR_CONSOLE_TELE"));
      }
      return true;
    } else if (commandLabel.equalsIgnoreCase("bhdebug")) {
      doDebugChecking(sender);
      return true;
    }
    return false;
  }

  public void startBedTeleport(Player player, World world, int teleportDelay){
    if (teleportDelay == 0) {
      if (chargePlayerAccount(player, bedTpCost)){
        teleToBed(player, world);
      }
    }

     player.sendMessage(getLocaleString("BH_DELAYED"));
     Location playerLocation = player.getLocation().clone(); //clone to get value, not a reference. just to be sure.
     getServer().getScheduler().runTaskLater(plugin, () -> {
       // why not use .equals(_? because then rotation is taken into account, and ideally you'd want to be able to look around while the teleport is winding up.
       if (player.getLocation().getX() != playerLocation.getX() || player.getLocation().getY() != playerLocation.getY() || player.getLocation().getZ() != playerLocation.getZ()){
         player.sendMessage(getLocaleString("BH_MOVED_DELAY"));
       }
       else {
         if (chargePlayerAccount(player, bedTpCost)){
           teleToBed(player, world);
         }
       }
     }, teleportDelay * 20L);
  };

  public static final Main getPlugin() {
    return plugin;
  }
}
