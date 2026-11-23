# Gemini Phone Agent (Native Android App)

একটি সম্পূর্ণ নিজস্ব, সুরক্ষিত এবং ব্যাকগ্রাউন্ডে সক্ষম এআই কোডিং এজেন্ট অ্যাপ (Android)।

## 🌟 মূল বৈশিষ্ট্যসমূহ
1. **Background Foreground Service:** ফোন লক থাকলেও বা অন্য অ্যাপ ব্যবহার করলেও অ্যান্ড্রয়েড এটিকে মেমোরি থেকে কিল করবে না।
2. **Gemini 3.8 Flash Core:** গুগলের নতুন হাই-স্পিড মডেলের সাথে সরাসরি ডায়রেক্ট কানেকশন।
3. **Agent Tools (Function Calling):**
   - `read_file`: ওয়ার্কস্পেসের যেকোনো ফাইল পড়া।
   - `write_file`: স্বয়ংক্রিয়ভাবে নতুন কোড/স্ক্রিপ্ট তৈরি করা।
   - `list_files`: ফাইল ও ডিরেক্টরি তালিকা দেখা।
4. **১০০% নিরাপদ ও ব্যক্তিগত:**
   - কোনো থার্ড-পার্টি লাইব্রেরি বা ট্র্যাকিং নেই।
   - API Key শুধুমাত্র ফোনের নিজস্ব `Encrypted/Private SharedPreferences`-এ সংরক্ষিত থাকে।
5. **আধুনিক UI:** Jetpack Compose এবং Material 3 ডার্ক থিম।

## 📁 প্রজেক্ট স্ট্রাকচার
```
gemini-phone-agent/
├── app/
│   ├── src/main/java/com/example/geminiagent/
│   │   ├── MainActivity.kt                # অ্যাপ এন্ট্রি ও পারমিশন হ্যান্ডলার
│   │   ├── service/
│   │   │   └── AgentForegroundService.kt  # ব্যাকগ্রাউন্ড ফোরগ্রাউন্ড সার্ভিস ও ওয়েক-লক
│   │   ├── api/
│   │   │   └── GeminiApiClient.kt         # জেমিনি API এবং টুলস এক্সিকিউটর
│   │   ├── model/
│   │   │   └── ChatMessage.kt             # চ্যাট মেসেজ ডাটা মডেল
│   │   └── ui/
│   │       ├── AgentChatScreen.kt         # আধুনিক চ্যাট ইউআই
│   │       └── theme/Theme.kt             # থিম কনফিগারেশন
│   └── AndroidManifest.xml
├── build.gradle.kts
└── settings.gradle.kts
```

## 🚀 কীভাবে চালাবেন ও APK বিল্ড করবেন
1. **Android Studio** ওপেন করুন।
2. `Open` এ ক্লিক করে এই ফোল্ডারটি সিলেক্ট করুন:  
   `C:\Users\GEORGE\.gemini\antigravity\scratch\gemini-phone-agent`
3. Gradle Sync শেষ হলে **Build -> Build Bundle(s) / APK(s) -> Build APK(s)** এ ক্লিক করলেই আপনার ফোনের জন্য ইনস্টলযোগ্য `.apk` তৈরি হয়ে যাবে!
4. ফোনটি কম্পিউটারের সাথে কানেক্ট করে সরাসরি `Run` (সবুজ প্লে বাটন) চাপলে সরাসরি ফোনে ইনস্টল হয়ে যাবে।

## 🛠️ ভবিষ্যতে যেভাবে আপগ্রেড করবেন
- **ভয়েস ইনপুট যোগ করতে:** `SpeechRecognizer` যোগ করে সরাসরি মুখে কথা বলে কাজ করান।
- **নতুন টুলস যোগ করতে:** `GeminiApiClient.kt` ফাইলে গিয়ে নতুন নতুন ফাংশন (যেমন: নোটিফিকেশন পাঠানো, ব্যাটারি লেভেল চেক ইত্যাদি) যোগ করুন।
