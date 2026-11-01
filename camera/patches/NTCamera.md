# NTCamera bytecode changes

The shipped `NTCamera.apk` (16.0.01.25.00) differs from Nothing's build in
`classes4.dex` and `classes5.dex`. Each change in `classes4.dex` wraps a call that reaches Nothing OS framework code
in a `catch java.lang.Error`, so a `NoClassDefFoundError`/`LinkageError` on a
non-Nothing framework degrades the feature instead of crashing the camera:

| Class | What is guarded | Missing on AOSP |
|---|---|---|
| `CameraContext$OpenCameraStateCallback.onOpened` | `new ProxyCameraDevice(device)` | `android.hardware.camera2.ProxyCameraDevice` (Nothing framework) |
| `CameraActivity.onCreate` | analytics init (`NTCameraDataTracking`, `com.nothing.experience.AppTracking`) | NothingExperience |
| `CameraActivity.onCreate` | `new com.nothing.kafka.EventObserver(...)` | `nt-framework.jar` |
| `AbstractBaseMode` / `AbstractModeImpl` / `AbstractModeImpl$1` | pending-capture cleanup `ContentResolver.query` and its error log | Nothing MediaProvider columns |
| `SpecialTypeProvider$LRUCache.removeEldestEntry` | `Long.parseLong` on cache file names | - |
| `SettingCharacteristics.init` / `SettingContext.getCameraId` | First-run fallback when `pref_camera_id_key` is -1/null to prevent NPE in `CameraCharacteristics.get` | - |
| `NcfBokehMode.getCalibrationData` | Null check on `QC_DUAL_MODULE_INFO` and catch `Exception` before `.length` to prevent NPE in Portrait mode | Dual-camera calibration metadata |
| `UFSManager.saveImage` / `updateRequestPriority` | Null check `mSaveReQuestHandle` before `removeMessages` / `post` to prevent shutter capture NPE when HAL session drops | - |

When updating the APK, re-apply every hunk below to the new `classes4.dex`
(class/method names are stable across releases; register numbers may move).
Hunks are shown exactly as they exist in the shipped dex.

### com.nothing.cameracore.context.module.CameraContext$OpenCameraStateCallback
.method public onOpened(Landroid/hardware/camera2/CameraDevice;)V
```smali
    :try_start_0
    new-instance v0, Landroid/hardware/camera2/ProxyCameraDevice;

    invoke-direct {v0, p1}, Landroid/hardware/camera2/ProxyCameraDevice;-><init>(Landroid/hardware/camera2/CameraDevice;)V
    :try_end_0
    .catch Ljava/lang/Error; {:try_start_0 .. :try_end_0} :catch_0
```

### com.nothing.camera.activity.CameraActivity
.method public onCreate(Landroid/os/Bundle;)V
```smali
    :try_start_0
    invoke-static {}, Lcom/nothing/common/analytics/NTCameraDataTracking;->getInstance()Lcom/nothing/common/analytics/NTCameraDataTracking;

    move-result-object p1

    iget-object v2, p0, Lcom/nothing/camera/activity/CameraActivity;->mAppContext:Landroid/content/Context;

    invoke-static {v2}, Lcom/nothing/experience/AppTracking;->getInstance(Landroid/content/Context;)Lcom/nothing/experience/AppTracking;

    move-result-object v2

    invoke-virtual {p1, v2}, Lcom/nothing/common/analytics/NTCameraDataTracking;->setAppTracking(Lcom/nothing/experience/AppTracking;)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0
    .catch Ljava/lang/Error; {:try_start_0 .. :try_end_0} :catch_0
```

### com.nothing.camera.activity.CameraActivity
.method public onCreate(Landroid/os/Bundle;)V
```smali
    :try_start_1
    new-instance p1, Lcom/nothing/kafka/EventObserver;

    new-instance v2, Landroid/os/Handler;

    invoke-direct {v2}, Landroid/os/Handler;-><init>()V

    const-string v4, "foreground_app"

    invoke-direct {p1, p0, v2, v4}, Lcom/nothing/kafka/EventObserver;-><init>(Landroid/content/Context;Landroid/os/Handler;Ljava/lang/String;)V

    iput-object p1, p0, Lcom/nothing/camera/activity/CameraActivity;->mEventObserver:Lcom/nothing/kafka/EventObserver;
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_1
    .catch Ljava/lang/Error; {:try_start_1 .. :try_end_1} :catch_1
```

### com.nothing.camera.mode.AbstractBaseMode
.method static synthetic lambda$deleteProcessPacket$1(Lcom/nothing/common/storage/StoragePacket;Landroid/content/ContentResolver;Ljava/io/File;)V
```smali
    :try_start_1
    invoke-virtual/range {v4 .. v9}, Landroid/content/ContentResolver;->query(Landroid/net/Uri;[Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;)Landroid/database/Cursor;

    move-result-object p0
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_1
    .catch Ljava/lang/Error; {:try_start_1 .. :try_end_1} :catch_2
```

### com.nothing.camera.mode.AbstractBaseMode
.method static synthetic lambda$deleteProcessPacket$1(Lcom/nothing/common/storage/StoragePacket;Landroid/content/ContentResolver;Ljava/io/File;)V
```smali
    :try_start_e
    invoke-static {v3, v2, p0}, Lcom/nothing/common/utils/LogUtil;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)V
    :try_end_e
    .catch Ljava/lang/Exception; {:try_start_e .. :try_end_e} :catch_2
    .catch Ljava/lang/Error; {:try_start_e .. :try_end_e} :catch_2
```

### com.nothing.camera.mode.AbstractModeImpl
.method static synthetic lambda$deleteProcessPacket$1(Lcom/nothing/common/storage/StoragePacket;Landroid/content/ContentResolver;Ljava/io/File;)V
```smali
    :try_start_1
    invoke-virtual/range {v4 .. v9}, Landroid/content/ContentResolver;->query(Landroid/net/Uri;[Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;)Landroid/database/Cursor;

    move-result-object p0
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_1
    .catch Ljava/lang/Error; {:try_start_1 .. :try_end_1} :catch_2
```

### com.nothing.camera.mode.AbstractModeImpl
.method static synthetic lambda$deleteProcessPacket$1(Lcom/nothing/common/storage/StoragePacket;Landroid/content/ContentResolver;Ljava/io/File;)V
```smali
    :try_start_e
    invoke-static {v3, v2, p0}, Lcom/nothing/common/utils/LogUtil;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)V
    :try_end_e
    .catch Ljava/lang/Exception; {:try_start_e .. :try_end_e} :catch_2
    .catch Ljava/lang/Error; {:try_start_e .. :try_end_e} :catch_2
```

### com.nothing.camera.mode.AbstractModeImpl$1
.method static synthetic lambda$run$1(Lcom/nothing/common/storage/StoragePacket;Landroid/content/ContentResolver;[ZLjava/io/File;)V
```smali
    :try_start_1
    invoke-virtual/range {v4 .. v9}, Landroid/content/ContentResolver;->query(Landroid/net/Uri;[Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;)Landroid/database/Cursor;

    move-result-object p0
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_1
    .catch Ljava/lang/Error; {:try_start_1 .. :try_end_1} :catch_2
```

### com.nothing.camera.mode.AbstractModeImpl$1
.method static synthetic lambda$run$1(Lcom/nothing/common/storage/StoragePacket;Landroid/content/ContentResolver;[ZLjava/io/File;)V
```smali
    :try_start_e
    invoke-static {v3, v2, p0}, Lcom/nothing/common/utils/LogUtil;->e(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Throwable;)V
    :try_end_e
    .catch Ljava/lang/Exception; {:try_start_e .. :try_end_e} :catch_2
    .catch Ljava/lang/Error; {:try_start_e .. :try_end_e} :catch_2
```

### com.nothing.camera.provider.SpecialTypeProvider$LRUCache
.method private synthetic lambda$removeEldestEntry$0(Ljava/util/Map$Entry;)V
```smali
    :try_start_1
    invoke-static {p1}, Ljava/lang/Long;->parseLong(Ljava/lang/String;)J

    move-result-wide v9
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_0
    .catch Ljava/lang/Error; {:try_start_1 .. :try_end_1} :catch_1
```

### com.nothing.camera.mode.NcfBokehMode
.method private getCalibrationData()V
```smali
    invoke-virtual {v7, v2}, Landroid/hardware/camera2/CameraCharacteristics;->get(Landroid/hardware/camera2/CameraCharacteristics$Key;)Ljava/lang/Object;

    move-result-object v2

    check-cast v2, [B

    if-nez v2, :cond_qc_ok

    goto :catch_c2

    :cond_qc_ok
    ...
    :try_end_c2
    .catch Ljava/lang/Exception; {:try_start_9d .. :try_end_c2} :catch_c2
```

### com.nothing.algolib.cameraufs.UFSManager
.method public saveImage(JLcom/nothing/algolib/cameraufs/NtCamUfsImage;)Z
```smali
    iget-object v0, p0, Lcom/nothing/algolib/cameraufs/UFSManager;->mSaveReQuestHandle:Landroid/os/Handler;

    if-eqz v0, :cond_skip_save_remove

    const/4 v3, 0x2

    invoke-virtual {v0, v3}, Landroid/os/Handler;->removeMessages(I)V

    :cond_skip_save_remove
```


