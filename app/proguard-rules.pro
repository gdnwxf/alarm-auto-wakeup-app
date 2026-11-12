# 保留 Room 生成的实现类
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }

# 保留 Compose 运行时所需默认规则（AGP 已内置）
