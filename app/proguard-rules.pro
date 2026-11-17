# Shift Alarm release rules (T10i).
#
# Not needed here, already covered elsewhere:
# - Activity / Service / BroadcastReceiver / AppWidgetProvider from merged manifests:
#   AGP generates keep rules for every manifest component (aapt_rules.txt).
# - Room, DataStore, Glance, Koin, Compose, kotlinx.coroutines ship consumer rules in their AARs.
# - Koin DSL here is lambda-based (single { }, viewModel { }), no reflection lookup by class name.

# Enum names are persisted (DataStore keys "permission_requests_<NAME>", Room converters by name).
# Keep the constants and valueOf/values so renamed or merged enums can't change stored data.
-keepclassmembers enum com.walhalla.shiftalarm.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public static final ** *;
}

# Readable crash stacks in Play/RuStore consoles; mapping.txt maps names back.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
