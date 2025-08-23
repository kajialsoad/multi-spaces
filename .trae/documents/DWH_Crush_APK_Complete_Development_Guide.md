# DWH Crush APK - সম্পূর্ণ ডেভেলপমেন্ট গাইড

## 📱 প্রজেক্ট ওভারভিউ

**DWH (Dual Space Multi Space)** একটি অ্যান্ড্রয়েড অ্যাপ ক্লোনিং এবং ভার্চুয়াল স্পেস অ্যাপ্লিকেশন যা ব্যবহারকারীদের একই ডিভাইসে একাধিক অ্যাপ ইনস্ট্যান্স চালানোর সুবিধা দেয়।

### মূল তথ্য:

* **প্যাকেজ নাম**: `com.dualspace.multispace.androil`

* **ভার্শন**: 1.1.3 (কোড: 12)

* **টার্গেট SDK**: 27 (Android 8.1)

* **মিনিমাম SDK**: 21 (Android 5.0)

* **সাইজ**: প্রায় 11.9MB

***

## 🏗️ প্রজেক্ট স্ট্রাকচার

### মূল ডিরেক্টরি স্ট্রাকচার:

```
DWH™~ 08-1.1.3_src/
├── 📁 original/                    # মূল APK ফাইলসমূহ
│   ├── AndroidManifest.xml         # মূল ম্যানিফেস্ট ফাইল
│   └── META-INF/                   # সাইনিং তথ্য
├── 📁 smali_classes2/              # ডিকম্পাইল করা Java কোড (Smali ফরম্যাট)
│   ├── com/google/                 # Google Services
│   ├── com/lody/virtual/           # ভার্চুয়াল স্পেস কোর
│   ├── com/swift/sandhook/         # হুকিং ফ্রেমওয়ার্ক
│   └── com/vungle/warren/          # বিজ্ঞাপন SDK
├── 📁 smali_classes3/              # অতিরিক্ত Smali ক্লাস
├── 📁 lib/                         # নেটিভ লাইব্রেরি
│   ├── arm64-v8a/                  # 64-bit ARM লাইব্রেরি
│   └── armeabi-v7a/                # 32-bit ARM লাইব্রেরি
├── 📁 res/                         # রিসোর্স ফাইল
├── 📁 assets/                      # অ্যাসেট ফাইল
├── 📄 classes.dex                  # মূল DEX ফাইল
├── 📄 classes2.dex                 # অতিরিক্ত DEX ফাইল
├── 📄 classes3.dex                 # অতিরিক্ত DEX ফাইল
├── 📄 resources.arsc               # কম্পাইল করা রিসোর্স
├── 📄 apktool.yml                  # APKTool কনফিগারেশন
└── 📄 cloneSettings.json           # এনক্রিপ্টেড ক্লোন সেটিংস
```

***

## 🔧 প্রযুক্তিগত আর্কিটেকচার

### কোর কম্পোনেন্টসমূহ:

#### 1. **ভার্চুয়াল স্পেস ইঞ্জিন** (`com.lody.virtual`)

* অ্যাপ স্যান্ডবক্সিং

* প্রসেস আইসোলেশন

* ফাইল সিস্টেম ভার্চুয়ালাইজেশন

* অ্যাপ ক্লোনিং ম্যানেজমেন্ট

#### 2. **হুকিং সিস্টেম** (`com.swift.sandhook`)

* মেথড হুকিং

* রানটাইম মডিফিকেশন

* API ইন্টারসেপশন

* সিস্টেম কল রিডাইরেকশন

#### 3. **বিজ্ঞাপন সিস্টেম** (`com.vungle.warren`)

* ভিডিও বিজ্ঞাপন

* ব্যানার বিজ্ঞাপন

* রিওয়ার্ড বিজ্ঞাপন

* বিজ্ঞাপন ট্র্যাকিং

#### 4. **নেটিভ লাইব্রেরি**

* `libappcloner.so` - অ্যাপ ক্লোনিং কোর

* `libsandhook.so` - হুকিং ইঞ্জিন

* `libv++.so` - ভার্চুয়াল মেশিন

* `libEncryptorP.so` - এনক্রিপশন

***

## 🎯 মূল ফিচারসমূহ

### 1. **অ্যাপ ক্লোনিং**

* একাধিক অ্যাকাউন্ট সাপোর্ট

* ডেটা আইসোলেশন

* ইন্ডিপেন্ডেন্ট স্টোরেজ

* সিকিউর স্যান্ডবক্স

### 2. **ভার্চুয়াল স্পেস**

* প্রাইভেট এনভায়রনমেন্ট

* হিডেন অ্যাপ সাপোর্ট

* কাস্টম আইকন

* সিকিউরিটি লক

### 3. **পারফরমেন্স অপটিমাইজেশন**

* মেমরি ম্যানেজমেন্ট

* CPU অপটিমাইজেশন

* ব্যাটারি সেভিং

* ফাস্ট লঞ্চ

### 4. **সিকিউরিটি ফিচার**

* অ্যাপ লক

* ফিঙ্গারপ্রিন্ট সাপোর্ট

* ডেটা এনক্রিপশন

* প্রাইভেসি প্রোটেকশন

***

## 🛠️ ডেভেলপমেন্ট সেটআপ

### প্রয়োজনীয় টুলস:

1. **APKTool** (v2.4.0+)

   ```bash
   # ডাউনলোড এবং ইনস্টল
   wget https://raw.githubusercontent.com/iBotPeaches/Apktool/master/scripts/linux/apktool
   wget https://bitbucket.org/iBotPeaches/apktool/downloads/apktool_2.4.0.jar
   ```

2. **Java Development Kit (JDK 8+)**

   ```bash
   sudo apt install openjdk-8-jdk
   ```

3. **Android SDK**

   * Build Tools

   * Platform Tools

   * Android API 27

4. **Signing Tools**

   * Keytool

   * Jarsigner

   * Zipalign

### এনভায়রনমেন্ট সেটআপ:

```bash
# JAVA_HOME সেট করুন
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
export PATH=$PATH:$JAVA_HOME/bin

# Android SDK পাথ
export ANDROID_HOME=/path/to/android-sdk
export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools
```

***

## 🔨 বিল্ড প্রসেস

### 1. **APK ডিকম্পাইল**

```bash
# মূল APK ডিকম্পাইল করুন
apktool d DWH™~\ 08-1.1.3_src.apk -o decoded_apk

# ফ্রেমওয়ার্ক রিসোর্স ইনস্টল
apktool if framework-res.apk
```

### 2. **কোড মডিফিকেশন**

#### Smali কোড এডিট:

```smali
# উদাহরণ: অ্যাপ নাম পরিবর্তন
# res/values/strings.xml এ
<string name="app_name">ゆめ</string>

# Vungle বিজ্ঞাপন ডিসেবল
# com/vungle/warren/AdLoader.smali এ
.method public canPlayAd()Z
    .locals 1
    const/4 v0, 0x0  # false রিটার্ন
    return v0
.end method
```

### 3. **রিসোর্স মডিফিকেশন**

```bash
# নতুন আইকন যোগ করুন
cp new_icon.png res/mipmap-xxxhdpi/ic_launcher.png

# স্ট্রিং রিসোর্স আপডেট
vim res/values/strings.xml
```

### 4. **APK রিবিল্ড**

```bash
# APK রিবিল্ড
apktool b decoded_apk -o modified_app.apk

# Zipalign
zipalign -v 4 modified_app.apk aligned_app.apk
```

### 5. **APK সাইনিং**

```bash
# কীস্টোর তৈরি (প্রথমবার)
keytool -genkey -v -keystore my-release-key.keystore \
        -alias alias_name -keyalg RSA -keysize 2048 -validity 10000

# APK সাইন করুন
jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 \
          -keystore my-release-key.keystore aligned_app.apk alias_name

# ফাইনাল zipalign
zipalign -v 4 aligned_app.apk final_signed_app.apk
```

***

## 🎨 UI/UX কাস্টমাইজেশন

### থিম এবং কালার:

```xml
<!-- res/values/colors.xml -->
<resources>
    <color name="primary_color">#2196F3</color>
    <color name="accent_color">#FF4081</color>
    <color name="background_color">#FFFFFF</color>
</resources>
```

### লেআউট মডিফিকেশন:

```xml
<!-- res/layout/activity_main.xml -->
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">
    
    <!-- কাস্টম UI এলিমেন্ট -->
    
</LinearLayout>
```

***

## 🔐 সিকিউরিটি এবং অবফাসকেশন

### কোড অবফাসকেশন:

1. **ক্লাস নেম অবফাসকেশন**

   * `com.example.MyClass` → `o00ooOOOo0OO`

   * `com.example.Helper` → `oOO00OO0Oo0`

2. **মেথড নেম অবফাসকেশন**

   * `getUserData()` → `Oo00oOooOoooO()`

   * `processData()` → `oOoOOooO0o()`

### এনক্রিপশন:

```java
// cloneSettings.json এনক্রিপ্টেড ডেটা
// AES-256 এনক্রিপশন ব্যবহার করা হয়েছে
```

***

## 🧪 টেস্টিং এবং ডিবাগিং

### টেস্টিং চেকলিস্ট:

* [ ] APK ইনস্টলেশন

* [ ] অ্যাপ লঞ্চ

* [ ] ক্লোনিং ফাংশনালিটি

* [ ] পারফরমেন্স টেস্ট

* [ ] মেমরি লিক চেক

* [ ] ক্র্যাশ টেস্ট

### ডিবাগিং টুলস:

```bash
# Logcat মনিটরিং
adb logcat | grep "DWH"

# মেমরি ব্যবহার চেক
adb shell dumpsys meminfo com.dualspace.multispace.androil

# CPU ব্যবহার মনিটর
adb shell top | grep "androil"
```

***

## 📦 ডিপ্লয়মেন্ট

### রিলিজ প্রস্তুতি:

1. **কোড অপটিমাইজেশন**
2. **রিসোর্স কম্প্রেশন**
3. **অবফাসকেশন**
4. **সাইনিং**
5. **টেস্টিং**

### ডিস্ট্রিবিউশন:

```bash
# APK সাইজ চেক
ls -lh final_signed_app.apk

# APK ভেরিফিকেশন
aapt dump badging final_signed_app.apk
```

***

## 🔧 ট্রাবলশুটিং

### সাধারণ সমস্যা এবং সমাধান:

#### 1. **Parsing Package Error**

```bash
# সমাধান: সঠিক সাইনিং এবং zipalign
zipalign -v 4 input.apk output.apk
jarsigner -verify -verbose -certs output.apk
```

#### 2. **Installation Failed**

```bash
# পুরাতন ভার্শন আনইনস্টল
adb uninstall com.dualspace.multispace.androil

# নতুন APK ইনস্টল
adb install -r final_signed_app.apk
```

#### 3. **App Crashes**

```bash
# ক্র্যাশ লগ দেখুন
adb logcat | grep "AndroidRuntime"

# Smali কোড চেক করুন
# সিনট্যাক্স এরর খুঁজুন
```

***

## 📚 অ্যাডভান্সড টপিকস

### 1. **কাস্টম হুকিং**

```smali
# SandHook ব্যবহার করে মেথড হুক
.method public static hookMethod()V
    .locals 2
    
    # হুকিং লজিক
    invoke-static {}, Lcom/swift/sandhook/SandHook;->hook()V
    
    return-void
.end method
```

### 2. **পারফরমেন্স অপটিমাইজেশন**

```smali
# মেমরি অপটিমাইজেশন
.method private optimizeMemory()V
    .locals 1
    
    invoke-static {}, Ljava/lang/System;->gc()V
    
    return-void
.end method
```

### 3. **নেটিভ কোড ইন্টিগ্রেশন**

```c
// libappcloner.so এর জন্য JNI ইন্টারফেস
JNIEXPORT jstring JNICALL
Java_com_dualspace_multispace_NativeHelper_getCloneInfo
  (JNIEnv *env, jobject thiz) {
    return (*env)->NewStringUTF(env, "Clone Info");
}
```

***

## 🎯 ভবিষ্যৎ উন্নতি

### পরিকল্পিত ফিচার:

1. **AI-Powered ক্লোনিং**
2. **ক্লাউড সিঙ্ক**
3. **অ্যাডভান্সড সিকিউরিটি**
4. **পারফরমেন্স মনিটরিং**
5. **ক্রস-প্ল্যাটফর্ম সাপোর্ট**

### টেকনিক্যাল রোডম্যাপ:

* Android 12+ সাপোর্ট

* 64-bit অপটিমাইজেশন

* মডার্ন UI/UX

* এনহান্সড সিকিউরিটি

***

## 📞 সাপোর্ট এবং কমিউনিটি

### ডেভেলপার রিসোর্স:

* **APKTool ডকুমেন্টেশন**: <https://ibotpeaches.github.io/Apktool/>

* **Smali গাইড**: <https://github.com/JesusFreke/smali>

* **Android ডেভেলপার গাইড**: <https://developer.android.com/>

### কমিউনিটি:

* XDA Developers Forum

* Reddit r/androiddev

* Stack Overflow

***

## ⚖️ লাইসেন্স এবং আইনি বিষয়

### গুরুত্বপূর্ণ নোট:

1. **শুধুমাত্র শিক্ষামূলক উদ্দেশ্যে ব্যবহার করুন**
2. **মূল অ্যাপের কপিরাইট সম্মান করুন**
3. **বাণিজ্যিক ব্যবহারের আগে আইনি পরামর্শ নিন**
4. **ম্যালওয়্যার বা ক্ষতিকর কোড যোগ করবেন না**

***

## 📝 চেকলিস্ট: A to Z ডেভেলপমেন্ট

### প্রস্তুতি পর্যায়:

* [ ] ডেভেলপমেন্ট এনভায়রনমেন্ট সেটআপ

* [ ] প্রয়োজনীয় টুলস ইনস্টল

* [ ] মূল APK ব্যাকআপ

### ডেভেলপমেন্ট পর্যায়:

* [ ] APK ডিকম্পাইল

* [ ] কোড বিশ্লেষণ

* [ ] ফিচার মডিফিকেশন

* [ ] UI/UX কাস্টমাইজেশন

* [ ] রিসোর্স আপডেট

### টেস্টিং পর্যায়:

* [ ] লোকাল টেস্টিং

* [ ] পারফরমেন্স টেস্ট

* [ ] সিকিউরিটি চেক

* [ ] ক্র্যাশ টেস্ট

### ডিপ্লয়মেন্ট পর্যায়:

* [ ] APK রিবিল্ড

* [ ] সাইনিং

* [ ] ফাইনাল টেস্টিং

* [ ] ডিস্ট্রিবিউশন

***

**সর্বশেষ আপডেট**: জানুয়ারি 2025\
**ভার্শন**: 1.0\
**লেখক**: DWH Development Team

> **দাবিত্যাগ**: এই ডকুমেন্টটি শুধুমাত্র শিক্ষামূলক উদ্দেশ্যে তৈরি। যেকোনো বাণিজ্যিক বা অবৈধ ব্যবহারের জন্য লেখক দায়ী নন।

