# Altyazı Çevirici (PS5 → Tablet)

PS5 doğrudan HDMI ile LG TV'ye bağlı kalır, oyunda gecikme olmaz. Tablet, kamerayla TV'deki
altyazıyı okur, **offline** olarak Türkçeye çevirir, büyük harflerle gösterir ve **sesli okur**.

## Nasıl çalışır
1. Kamera (harici USB kamera varsa o, yoksa tabletin arka kamerası) TV'ye bakar.
2. Sarı kutuyla seçtiğin bölgede saniyede ~5 kez OCR yapılır (ML Kit, cihaz üzerinde).
3. Aynı metin art arda 2 karede okununca altyazı "kararlı" sayılır; önceki altyazıya çok benziyorsa atlanır.
4. ML Kit Translate ile EN→TR çevrilir (offline). Aynı replikler önbellekten gelir.
5. Çeviri ekranda gösterilir ve Android TTS ile Türkçe okunur. Yeni altyazı gelince eski okuma kesilir.

## Derleme ve kurulum
1. Android Studio'yu kur (Ladybug veya üstü) ve bu klasörü **Open** ile aç. Gradle senkronizasyonu bağımlılıkları indirir.
2. Tablette **Geliştirici seçenekleri**ni aç: Ayarlar > Tablet hakkında > Yazılım bilgileri > *Yapım numarası*na 7 kez dokun.
3. Geliştirici seçeneklerinden **USB hata ayıklama**'yı aç, tableti USB ile PC'ye bağla.
4. Android Studio'da ▶ **Run**.
   - Alternatif: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` dosyasını tablete kopyalayıp aç ("bilinmeyen kaynaklar" izni ister).

## İlk açılış
- **İnternet bir kez gerekli:** EN→TR çeviri modeli (~30 MB) iner, sonra tamamen offline çalışır.
- **Türkçe ses:** Ses çıkmazsa Ayarlar > Genel yönetim > Metin okuma çıkışı'ndan Samsung veya Google TTS motorunu seçip Türkçe sesi indir.
- Sarı kutuyu oyunun altyazı satırlarının üstüne getir (içinden sürükle, köşelerinden boyutla). Konum kaydedilir.
- **Tam ekran yazı** düğmesi kamerayı gizler; geri dönmek için yazıya dokun.

## Kurulum ipuçları (OCR kalitesi için önemli)
- Oyunun altyazı ayarlarından **en büyük yazı boyutu** ve **arka planlı (koyu kutulu) altyazı**yı aç. OCR doğruluğunu en çok bu artırır.
- Kamera TV'ye mümkün olduğunca **dik** baksın; kameranın bakış açısına lamba ya da pencere yansıması girmesin.
- Kutuyu sadece altyazı satırlarına daralt. Fazladan arayüz yazısı girerse çeviri bozulur.

## Harici kamera
- Tablet USB-C'ye takılan standart UVC webcam'leri `LENS_FACING_EXTERNAL` olarak görür. Takınca uygulama otomatik geçer, olmazsa **Kamera değiştir** düğmesine bas veya uygulamayı yeniden aç.
- 1080p, otomatik odaklı bir webcam yeterli. Kamerayı TV'nin altına, altyazıya yakın konuma sabitle.
- Passthrough'lu HDMI capture kart da aynı şekilde USB kamera gibi görünür; aldığında kod değişmeden çalışır (PS5'te HDCP'yi kapatman gerekir).

## Ayarlanabilir değerler
| Yer | Değer | Etki |
|---|---|---|
| `MainActivity.minIntervalMs` | 200 | OCR sıklığı. Düşürürsen daha hızlı tepki, daha çok ısınma |
| `SubtitlePipeline` `candidateHits < 2` | 2 | Kararlılık için gereken kare sayısı. 1 yaparsan daha hızlı, ama yarım okumalar artar |
| `SubtitlePipeline` `setSpeechRate` | 1.15 | Okuma hızı |
| `TranslateLanguage.ENGLISH` | EN | Oyun başka dildeyse kaynak dili değiştir (OCR Latin alfabesi içindir) |
