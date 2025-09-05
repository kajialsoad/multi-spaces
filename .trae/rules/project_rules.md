code ruled : /*
========================================
   🔐 Kotlin Safe Coding Rules (Munna)
========================================

1️⃣ Before Coding
- Requirement পরিষ্কার করো
- Architecture / Flow আগে লিখে নাও
- Proper naming convention follow করো (camelCase, PascalCase)

2️⃣ While Coding
- সব variable initialize করে use করো
- Null safety (?.  ?:  !!) ঠিকভাবে ব্যবহার করো
- try-catch block ব্যবহার করো error-prone জায়গায়
- Network / DB কাজ সব background thread এ (Coroutines ব্যবহার করো)
- UI update সবসময় Main thread এ করো
- Long function এড়িয়ে ছোট ছোট function বানাও (Single Responsibility)

3️⃣ Error Handling
- API call → timeout + exception handle
- Database / File I/O → try-catch ব্যবহার করো
- Log.e(TAG, "error msg", e) দিয়ে meaningful log দাও

4️⃣ Build Time
- Lint warning কখনো ignore করো না
- Gradle dependencies update consistent রাখো
- Clean + Rebuild করে তারপর APK build করো
- Proguard / R8 rules configure করো

5️⃣ Testing
- প্রতিটা feature লিখেই test করো
- Edge case test (null input, slow net, empty list)
- APK build করার আগে পুরো project test করো

6️⃣ Golden Rule
👉 "Think Twice → Code Once"
👉 Stack trace পড়ো, shortcut code লিখো না
👉 IDE warning/error অবহেলা কোরো না

========================================
*/
Deep Rules for Kotlin Development (Munna’s IDE Guide)
1️⃣ Project Setup Rules

Proper Package Structure

com.munna.myapp
    ├── data        (DB, API, Repository)
    ├── domain      (UseCases, Business logic)
    ├── ui          (Activities, Fragments, ViewModels)
    ├── utils       (Helpers, Constants)


Gradle Convention

সর্বদা ext / libs.versions.toml দিয়ে dependency manage করো।

সব dependency একসাথে রাখো, random add কোরো না।

2️⃣ Code Writing Rules
🔹 Kotlin Language Best Practices

Always prefer val over var

Use data class for models

Use sealed class for state handling

Use extension functions for utilities

Never use !! (force unwrap)

Prefer when over multiple if

🔹 Example
// ❌ Bad
var name: String? = null
if (name != null) {
   println(name)
}

// ✅ Good
val name: String? = "Munna"
println(name ?: "Unknown")

3️⃣ Error Safety Rules

সব API call → Coroutine + try-catch + Result wrapper

সব DB query → safe call + error log

সব null input → requireNotNull বা default value

🔹 Example (Network Safe Call)
suspend fun <T> safeApiCall(apiCall: suspend () -> T): Result<T> {
    return try {
        Result.success(apiCall.invoke())
    } catch (e: Exception) {
        Result.failure(e)
    }
}

4️⃣ Architecture Rules

👉 Follow MVVM + Clean Architecture

UI Layer → Activity/Fragment + ViewModel

Domain Layer → UseCase (business rules)

Data Layer → Repository + API + DB

🔹 Example Flow
UI -> ViewModel -> UseCase -> Repository -> API/DB

5️⃣ IDE Rules

Code Style Format On Save (Android Studio → Settings → Code Style → Kotlin → enable)

Lint / Inspection Enable (Yellow = fix করো, Red = build fail)

SonarLint plugin install করো → code smell detect করবে

Git integration → প্রতিদিন commit করো ছোট ছোট chunk এ

6️⃣ Testing Rules

Unit Test লিখো (JUnit, Mockito, Truth)

UI Test (Espresso) → কমপক্ষে login/critical path টেস্ট করো

Fake API response test করো (MockWebServer দিয়ে)

7️⃣ Build & Release Rules

Build variant আলাদা করো (debug, release, staging)

Proguard / R8 properly configure করো → sensitive class obfuscate করো

Crashlytics integrate করো runtime error ধরতে

8️⃣ Golden Discipline (Professional Dev Mindset)

❌ কোনো warning/error ignore করা যাবে না

✅ প্রতিদিন minimum একবার Clean + Rebuild

✅ প্রতিটা feature-এর জন্য আলাদা branch

✅ Merge করার আগে self code review করো

✅ Push করার আগে “Run All Tests”

🟢 Final Rule: Munna’s Developer Oath

👉 IDE খুললে মাথায় রাখব:

"আমি code লিখব নিজের জন্য না, বরং একজন ভবিষ্যৎ developer-এর জন্য"

"আমি shortcut code লিখব না"

"আমি প্রতিটা bug কে teacher হিসেবে নেব"















Clone করা অ্যাপ খুললে নতুন করে Sign In করতে হবে (ঠিক যেমন Chrome-এর Incognito Mode এ হয়)।

Sign In করার পর ব্যবহারকারী লগ ইন অবস্থায় অ্যাপ চালাতে পারবে।

Clone করা অ্যাপ Exit করলেও ওই Clone-এর লগ ইন ডেটা সেভ থাকবে।

আবার + আইকনে চাপ দিলে নতুন করে আরেকটা Clone তৈরি হবে → আরেকটা নতুন লগ ইন করা যাবে।




✅ কেন দরকার: একসাথে দুইটা WhatsApp, Facebook, বা অন্য অ্যাপ চালাতে পারবেন – আলাদা কাজে ব্যবহার করার জন্য।

সহজ অ্যাকাউন্ট স্যুইচিং
👉 কোনো লগইন বা সাইন আপ ছাড়াই সরাসরি ব্যবহার।
✅ কেন দরকার: ব্যবহারকারীর সময় বাঁচবে, ঝামেলাহীন অভিজ্ঞতা পাবে।

ডাটা সেপারেশন (Data Separation)
👉 Clone করা অ্যাপ খুললে নতুন করে Sign In করতে হবে (ঠিক যেমন Chrome-এর Incognito Mode এ হয়)।

Sign In করার পর ব্যবহারকারী লগ ইন অবস্থায় অ্যাপ চালাতে পারবে।

Clone করা অ্যাপ Exit করলেও ওই Clone-এর লগ ইন ডেটা সেভ থাকবে।

আবার + আইকনে চাপ দিলে নতুন করে আরেকটা Clone তৈরি হবে → আরেকটা নতুন লগ ইন করা যাবে।

Custom Icon & Name
👉 ক্লোন করা অ্যাপের নাম ও আইকন পরিবর্তন করা যাবে।
✅ কেন দরকার: কোনটা আসল আর কোনটা ক্লোন তা সহজে চিনতে সুবিধা হবে।

Lightweight & Fast
👉 কম স্টোরেজ ও কম RAM ব্যবহার করবে।
✅ কেন দরকার: সব ধরনের মোবাইলেই স্মুথলি চলবে, লো-এন্ড ডিভাইসেও সমস্যা হবে না।

নিরাপত্তা ফিচার (Safe & Secure)
👉 অ্যাপ ইনস্টল করার সময় “Harmful App” ওয়ার্নিং আসবে না।
✅ কেন দরকার: ব্যবহারকারীর আস্থা তৈরি হবে এবং Play Store পলিসি মানা হবে।
ফিচার (Feature)	কেন দরকার (Benefit)
Multi App Clone	একই অ্যাপের একাধিক কপি চালানো যাবে (যেমন একসাথে ২টা WhatsApp বা Facebook ব্যবহার)।
সহজ অ্যাকাউন্ট স্যুইচিং	কোনো লগইন/সাইন আপ ছাড়াই সরাসরি ব্যবহার → সময় বাঁচাবে ও ইউজার-ফ্রেন্ডলি হবে।
ডাটা সেপারেশন	প্রতিটি ক্লোন অ্যাপের আলাদা ডেটা ও সেটিংস থাকবে → একটার তথ্য অন্যটার সাথে মিশবে না, প্রাইভেসি সুরক্ষিত।
Custom Icon & Name	ক্লোন অ্যাপের নাম ও আইকন পরিবর্তন করা যাবে → কোনটা আসল আর কোনটা ক্লোন সহজে চেনা যাবে।
Lightweight & Fast	কম স্টোরেজ ও কম RAM ব্যবহার করবে → লো-এন্ড মোবাইলেও স্মুথলি চলবে।
নিরাপত্তা ফিচার	ইনস্টল করার সময় “Harmful App” ওয়ার্নিং আসবে না → ব্যবহারকারীর আস্থা বাড়বে এবং Play Store পলিসি মানা হবে।