# Gemini ChatBot - Android Chat Application

A modern Android chat application built with Jetpack Compose that implements Firebase Authentication and integrates with Google's Gemini AI model for intelligent conversations.

## Features

- **Authentication System**
  - Email/Password Authentication
  - Password Reset Functionality
  - Secure User Management
  - Clean Authentication UI

- **Chat Interface**
  - Real-time Message Updates
  - Message History
  - Typing Indicators
  - Suggestion Chips for Quick Responses


- **Technical Stack**
  - Kotlin
  - Jetpack Compose
  - Firebase Authentication
  - MVVM Architecture
  - Coroutines & Flow
  - Material Design 3


## Getting Started

### Prerequisites

- Android Studio Arctic Fox or later
- JDK 11 or later
- Android SDK 21 or later
- Firebase Account

### Installation

1. Clone the repository
```bash
git clone https://github.com/Zyrexam/Gemini-ChatBot.git
```

2. Open the project in Android Studio

3. Create a Firebase project and add your `google-services.json` file to the app directory

4. Enable Authentication in Firebase Console
   - Go to Firebase Console
   - Select your project
   - Navigate to Authentication
   - Enable Email/Password authentication

5. Build and run the application

## Project Structure

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/example/gemini_chatbot/
│   │   │   ├── MainActivity.kt
│   │   │   ├── AuthScreen.kt
│   │   │   ├── AuthViewModel.kt
│   │   │   ├── ChatScreen.kt
│   │   │   └── ChatViewModel.kt
│   │   └── res/
│   │       ├── drawable/
│   │       ├── layout/
│   │       └── values/
└── build.gradle
```

## Architecture

The application follows MVVM (Model-View-ViewModel) architecture pattern:

- **View**: Compose UI components (AuthScreen, ChatScreen)
- **ViewModel**: AuthViewModel, ChatViewModel
- **Model**: Firebase Authentication, Firestore Database

## Contributing

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request


## Acknowledgments

- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Firebase](https://firebase.google.com/)
- [Material Design](https://material.io/)
- [Google Gemini AI](https://cloud.google.com/gemini/docs/api-and-reference)
