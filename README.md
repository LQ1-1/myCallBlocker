# 🛡️ MyCallBlocker (电话拦截助手)

MyCallBlocker is a lightweight, privacy-focused Android call screening application built with Jetpack Compose. It helps users block unwanted calls based on customizable rules while keeping a clean and modern interface.

MyCallBlocker 是一个基于 Jetpack Compose 构建的轻量级、注重隐私的 Android 电话拦截应用。它可以帮助用户根据自定义规则拦截骚扰电话，同时保持简洁现代的界面。

## ✨ Features (功能特性)

*   **📞 Call Blocking**: Automatically intercepts unwanted calls using Android's `CallScreeningService`.
    *   (电话拦截：使用 Android 原生服务自动拦截所有来电除了通讯录中的号码)
*   **🚫 Blocking Rules**:
    *   Block all numbers not in contacts (Whitelist mode).
    *   Block specific prefixes or numbers (Future plan).
    *   (拦截规则：支持白名单模式，仅允许通讯录好友呼入)
*   **📝 Call Logs**: detailed history of blocked and allowed calls with interception reasons.
    *   (拦截记录：详细记录已拦截和已放行的通话及其原因)
*   **🌍 Multi-language Support**: Seamless switching between **English** and **Chinese**, with app restart support.
    *   (多语言支持：支持中英文无缝切换，应用内即时生效)
*   **🎨 Modern UI**: Built fully with **Jetpack Compose** and Material Design 3.
    *   (现代界面：完全使用 Jetpack Compose 和 Material Design 3 构建)
*   **📱 Optimized UX**: Sticky headers and scrollable layouts for better log viewing.
    *   (体验优化：采用吸顶标题和滚动布局，方便查看大量记录)

## 🛠️ Tech Stack (技术栈)

*   **Language**: Kotlin
*   **UI Framework**: Jetpack Compose (Material 3)
*   **Architecture**: MVVM (Model-View-ViewModel) pattern
*   **Database**: SQLite (SQLiteOpenHelper)
*   **Android APIs**:
    *   `CallScreeningService` (For interception)
    *   `RoleManager` (For requesting screening role)
    *   `ContactsContract` (For whitelist verification)

## 📸 Screenshots (截图展示)

|                      Home Screen (主页)                      |                 Settings & Logs (记录与设置)                 |                  Language Switch (语言切换)                  |
| :----------------------------------------------------------: | :----------------------------------------------------------: | :----------------------------------------------------------: |
| ![Screenshot_20260113_201356_MyCallBlocker](/Users/zero/Documents/c_idea_code/MyCallBlocker-GitLoader/myCallBlocker/demo/Screenshot_20260113_201356_MyCallBlocker.jpg) | ![Screenshot_20260113_201400_MyCallBlocker](/Users/zero/Documents/c_idea_code/MyCallBlocker-GitLoader/myCallBlocker/demo/Screenshot_20260113_201400_MyCallBlocker.jpg) | ![Screenshot_20260113_201409_MyCallBlocker](/Users/zero/Documents/c_idea_code/MyCallBlocker-GitLoader/myCallBlocker/demo/Screenshot_20260113_201409_MyCallBlocker.jpg) |

*(Note: Please create a folder named `screenshots` in your project root and add your app screenshots there.)*

## 🚀 Getting Started (如何运行)

1.  **Clone the repository**:
    ```bash
    git clone https://github.com/LQ1-1/myCallBlocker
    ```
2.  **Open in Android Studio**:
    *   File -> Open -> Select the project folder.
3.  **Build and Run**:
    *   Connect your Android device or use an Emulator.
    *   Click the green **Run** button.
4.  **Permissions**:
    *   On the first launch, please grant **Call Screening Role** and **Read Contacts** permissions for the app to function correctly.

## 📄 License

This project is licensed under the **GPL-3.0 License** - see the [LICENSE](LICENSE) file for details.
