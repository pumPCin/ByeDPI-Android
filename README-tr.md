# ByeByeDPI Android
[Русский](README.md) |[English](README-en.md)|Türkçe

<div style="text-align: center;">
  <img alt="ByeDPI Logo" src="https://github.com/romanvht/ByeDPIAndroid/raw/master/.github/images/logo.svg" width="100%" height="200px">
</div>

---

ByeDPI'yi yerel olarak çalıştıran ve tüm trafiği bunun üzerinden yönlendiren bir Android uygulaması.

Kararlı bir çalışma için ayarları yapmanız gerekebilir. Farklı ayarlar hakkında daha fazla bilgiye [ByeDPI dökümantasyonundan](https://github.com/hufrea/byedpi/blob/v0.13/README.md) ulaşabilirsiniz.

Bu uygulama **VPN** değildir. Trafiği yönlendirmek için Android'in VPN modunu kullanır ancak herhangi bir veriyi uzak bir sunucuya iletmez. Trafiği şifrelemez veya IP adresinizi gizlemez.

Bu uygulama, [ByeDPIAndroid](https://github.com/dovecoteescapee/ByeDPIAndroid) uygulamasının bir çatallamasıdır.

---

### Özellikler
* Cihaz başlatıldığında hizmetin otomatik başlatılması
* Komut satırı parametrelerinin listelerinin kaydedilmesi
* Android TV/BOX ile geliştirilmiş uyumluluk
* Uygulama başına bölünmüş tünelleme
* Ayarları içe/dışa aktarma

### Kullanım
* Otomatik başlatmayı etkinleştirmek için ayarlarda seçeneği aktifleştirin.
* İlk başta VPN'e bağlanarak isteği kabul etmeniz önerilir.
* Bundan sonra, cihaz başlatıldığında, uygulama ayarlara göre (VPN/Proxy) hizmeti otomatik olarak başlatacaktır.
* Android TV/BOX kullanıyorsanız ve Ethernet bağlantınız VPN'e bağlanırken kopuyorsa, beyaz liste modunu etkinleştirip VPN üzerinden çalışması gereken uygulamaları belirleyin (örneğin, YouTube).
* [SberBOX](sbox.md) için kılavuz

### Türkiye İle İlgili Destek İçin
* Discord: [nyaex](https://github.com/nyaexx) veya [shouyuma](https://github.com/Hamzahsl)


### ByeByeDPI'yi AdGuard ile nasıl kullanırım?
* ByeByeDPI'yi proxy modunda başlatın.
* ByeByeDPI'yi AdGuard dışlamalarına "Uygulama Yönetimi" sekmesinde ekleyin.
* AdGuard ayarlarında, proxy'i belirtin:
```plaintext
Proxy Türü: SOCKS5
Host: 127.0.0.1
Port: 1080 (varsayılan)
