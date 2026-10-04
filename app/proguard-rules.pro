# Shizuku AIDL interface - must not be renamed or removed
-keep class moe.shizuku.server.IShizukuService { *; }
-keep class moe.shizuku.server.IShizukuService$* { *; }
-keep class rikka.shizuku.Shizuku { *; }
-keep class rikka.shizuku.ShizukuProvider { *; }

# App BroadcastReceivers - referenced by name in AndroidManifest.xml
-keep class dev.yoanndev90.statusbarhider.BootReceiver { *; }
-keep class dev.yoanndev90.statusbarhider.control.ControlReceiver { *; }
-keep class dev.yoanndev90.statusbarhider.widget.BarWidgetProvider { *; }
