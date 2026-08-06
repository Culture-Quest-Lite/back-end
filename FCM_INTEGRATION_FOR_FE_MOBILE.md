Để thông báo có thể "bay" từ Firebase Console (hoặc Backend của bạn) xuống đúng thiết bị của người dùng, chúng ta sẽ đi qua từng bước cài đặt cho cả 2 môi trường Web Frontend và Mobile (React Native).

### **PHẦN 1: TÍCH HỢP CHO FRONTEND WEB (ReactJS / Vue / HTML)**

Đối với Web, trình duyệt yêu cầu một file Service Worker chạy ngầm để nhận thông báo kể cả khi người dùng không mở tab trang web đó.

**Bước 1: Tạo file Service Worker (Chạy ngầm)**

Bạn phải tạo một file có tên chính xác là `firebase-messaging-sw.js` và đặt nó ở **thư mục gốc public** của dự án (ví dụ `public/firebase-messaging-sw.js` trong React).

*Nội dung file `firebase-messaging-sw.js`:*

```javascript
importScripts('https://www.gstatic.com/firebasejs/10.8.0/firebase-app-compat.js');  
importScripts('https://www.gstatic.com/firebasejs/10.8.0/firebase-messaging-compat.js');  
  
const firebaseConfig = {  
    apiKey: "AIzaSy_KEY_CỦA_BẠN",  
    authDomain: "culture-quest-lite-bfa6a.firebaseapp.com",  
    projectId: "culture-quest-lite-bfa6a",  
    storageBucket: "culture-quest-lite-bfa6a.appspot.com",  
    messagingSenderId: "SENDER_ID_CỦA_BẠN",  
    appId: "APP_ID_CỦA_BẠN"  
};  
  
firebase.initializeApp(firebaseConfig);  
const messaging = firebase.messaging();  
  
messaging.onBackgroundMessage(function(payload) {  
  console.log('[firebase-messaging-sw.js] Đã nhận tin nhắn ngầm.', payload);  
  const notificationTitle = payload.notification.title;  
  const notificationOptions = {  
    body: payload.notification.body,  
    icon: '/logo.png' 
  };  
  
  self.registration.showNotification(notificationTitle, notificationOptions);  
});  

```

**Bước 2: Cài đặt Firebase SDK vào project FE**

Mở terminal tại thư mục project Frontend chạy lệnh:

```bash
npm install firebase  

```

**Bước 3: Viết service khởi tạo và lấy FCM Token**

Tạo một file ví dụ `firebase.js` (hoặc `firebase.ts`) để chứa logic xin quyền và lấy Token.

```javascript
import { initializeApp } from "firebase/app";  
import { getMessaging, getToken, onMessage } from "firebase/messaging";  
  
const firebaseConfig = {  
    apiKey: "AIzaSy_KEY_CỦA_BẠN",  
    projectId: "culture-quest-lite-bfa6a",  
    messagingSenderId: "SENDER_ID_CỦA_BẠN",  
    appId: "APP_ID_CỦA_BẠN"  
};  
  
const app = initializeApp(firebaseConfig);  
const messaging = getMessaging(app);  
  
export const requestForToken = async (userId) => {  
  try {  
    const permission = await Notification.requestPermission();  
    if (permission === "granted") {  
      const currentToken = await getToken(messaging, {   
          vapidKey: "VAPID_KEY_CỦA_BẠN_BEl6n..."   
      });  
        
      if (currentToken) {  
        console.log("FCM Token hiện tại:", currentToken);  
        return currentToken;  
      } else {  
        console.log("Không lấy được token.");  
      }  
    } else {  
      console.log("Người dùng từ chối cấp quyền thông báo.");  
    }  
  } catch (err) {  
    console.log("Lỗi khi lấy token", err);  
  }  
};  
  
export const onMessageListener = () =>  
  new Promise((resolve) => {  
    onMessage(messaging, (payload) => {  
      resolve(payload);  
    });  
  });  

```

### **PHẦN 2: TÍCH HỢP CHO MOBILE (React Native)**

Môi trường Mobile (Native) không dùng thư viện firebase thông thường mà phải xài `@react-native-firebase`.

**Bước 1: Cài đặt thư viện**

Mở terminal tại project React Native chạy lệnh:

```bash
npm install @react-native-firebase/app @react-native-firebase/messaging  

```

*(Nếu làm iOS, chạy thêm lệnh: `cd ios && pod install`)*.

**Bước 2: Nạp cấu hình Native**

* **Android:** Tải file `google-services.json` từ Firebase Console bỏ vào thư mục `android/app/`.


* **iOS:** Tải file `GoogleService-Info.plist` từ Firebase Console và dùng Xcode để add vào thư mục gốc của project iOS. (Bắt buộc phải có Apple Developer Account để cấu hình APNs Key thì iOS mới nhận được Noti).



**Bước 3: Lắng nghe thông báo chạy ngầm (Background/Quit State)**

Mở file `index.js` (nằm ở thư mục ngoài cùng của project RN) và cấu hình **trước** khi App register:

```javascript
import {AppRegistry} from 'react-native';  
import App from './App';  
import {name as appName} from './app.json';  
import messaging from '@react-native-firebase/messaging';  
  
messaging().setBackgroundMessageHandler(async remoteMessage => {  
  console.log('Nhận thông báo khi chạy ngầm!', remoteMessage);  
});  
  
AppRegistry.registerComponent(appName, () => App);  

```

**Bước 4: Xin quyền, lấy Token và nghe Noti lúc mở App (Foreground)**

Mở file `App.js` (hoặc `App.tsx`) và thiết lập các hàm sau:

```javascript
import React, { useEffect } from 'react';  
import { Alert } from 'react-native';  
import messaging from '@react-native-firebase/messaging';  
  
const App = () => {  
  
  useEffect(() => {  
    requestUserPermission();  
      
    const unsubscribe = messaging().onMessage(async remoteMessage => {  
      Alert.alert('Thông báo mới!', remoteMessage.notification.title);  
      console.log('Nội dung:', JSON.stringify(remoteMessage));  
    });  
  
    return unsubscribe;  
  }, []);  
  
  async function requestUserPermission() {  
    const authStatus = await messaging().requestPermission();  
    const enabled =  
      authStatus === messaging.AuthorizationStatus.AUTHORIZED ||  
      authStatus === messaging.AuthorizationStatus.PROVISIONAL;  
  
    if (enabled) {  
      console.log('Đã cấp quyền thông báo:', authStatus);  
      getFcmToken();  
    }  
  }  
  
  async function getFcmToken() {  
    try {  
      const token = await messaging().getToken();  
      console.log('FCM Token Mobile:', token);  
        
    } catch (error) {  
      console.log('Lỗi lấy FCM Token', error);  
    }  
  }  
  
  return (  
    
  );  
};  
  
export default App;  

```