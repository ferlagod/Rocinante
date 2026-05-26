<div align="center">
  
<img src="https://i.imgur.com/BR0w42y.png" width="100" alt="Logo Rocinante"/>

# Rocinante

**Un cliente Android moderno, libre y federado para BookWyrm**

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0+-purple.svg)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4CAF50.svg)](https://developer.android.com/jetpack/compose)

</div>

Rocinante es una aplicación nativa para Android diseñada para conectarse a cualquier instancia de **[BookWyrm](https://joinbookwyrm.com/)**, la red social de lectura basada en ActivityPub. Con Rocinante, puedes llevar tu biblioteca a todas partes y actualizar tu progreso de lectura.

---

## 📱 Capturas de pantalla

<div align="center">
  <table>
    <tr>
      <td><img src="https://i.imgur.com/YiB61aV.png" width="250" alt="Pantalla de usuario"/></td>
      <td><img src="https://i.imgur.com/Rz6nxyO.png" width="250" alt="Mis Libros"/></td>
      <td><img src="https://i.imgur.com/F9KRRNI.png" width="250" alt="Actividad"/></td>
    </tr>
    <tr>
      <td><img src="https://i.imgur.com/sZaRpUL.png" width="250" alt="Buscar"/></td>
      <td><img src="https://i.imgur.com/coFN3Sw.png" width="250" alt="Actualizar progreso"/></td>
      <td><img src="https://i.imgur.com/SjWJLuY.png" width="250" alt="Mi Perfil"/></td>
    </tr>
  </table>
</div>

---

## ✨ Características Principales

📖 **Gestión de Estanterías**
- Organiza tus libros en estanterías personalizadas: *Leyendo*, *Por leer* y *Leídos*.
- Cambia fácilmente el estado de un libro con un solo toque.

📈 **Progreso de Lectura**
- Actualiza tu progreso indicando la página actual o el porcentaje leído.
- Añade comentarios y reseñas a tus actualizaciones.

🌐 **Línea de Tiempo (Timeline) Federada**
- Interactúa con publicaciones de tu red a través de ActivityPub.
- Da "me gusta", responde a comentarios, y lee las reseñas de otros usuarios desde la aplicación.

👥 **Perfiles y Seguidores**
- Explora los perfiles de otros lectores.
- Sigue y deja de seguir perfiles de manera rápida con listas interactivas.

🔍 **Búsqueda Avanzada y Escáner de Códigos de Barras**
- Busca libros en toda la instancia.
- Utiliza la cámara de tu móvil para escanear códigos de barras (ISBN) y encontrar rápidamente tus libros físicos.

🔔 **Recordatorios de Lectura**
- ¿Te cuesta crear el hábito? Activa notificaciones locales diarias que te recordarán amablemente continuar con tu libro actual.

---

## 🛠️ Tecnologías Utilizadas

Rocinante está construido bajo los estándares más recientes de desarrollo Android:
- **Kotlin:** Lenguaje de programación principal.
- **Jetpack Compose:** Interfaz de usuario (UI) totalmente declarativa bajo los lineamientos de *Material Design 3*.
- **Corrutinas (Coroutines) y Flow:** Para un manejo asíncrono y reactivo de los datos.
- **Retrofit & OkHttp:** Para la comunicación ágil con las APIs de BookWyrm y endpoints de ActivityPub.
- **WorkManager:** Para los recordatorios diarios en segundo plano.
- **100% Internacionalizado:** Soporte integrado para 14 idiomas.

---


## 📱 Uso Básico

1. **Inicia Sesión:** Introduce la dirección de tu instancia BookWyrm (ej. `bookwyrm.social`) y tus credenciales.
2. **Explora:** La pestaña principal te mostrará la actividad reciente de a quienes sigues.
3. **Tus Libros:** Ve a la pestaña de estanterías para ver lo que estás leyendo. Pulsa sobre el widget flotante para actualizar tu página rápidamente.
4. **Escáner:** Usa la lupa del menú y presiona el icono del código de barras para añadir a tu colección el libro que tienes entre manos.

***Debido a las limitaciones actuales de los servidores de BookWyrm, no es posible dar 'Me Gusta' a las publicaciones ni modificar tu Objetivo Anual de Lectura desde la app. Podrás ver tu línea de tiempo de seguidos, buscar libros y organizar tus estanterías.***

---

## 🤝 Contribuciones

Este es un proyecto impulsado por mí para la comunidad. Eres libre de informar sobre *bugs* o sugerir funcionalidades. Si deseas aportar código, ponte en contacto conmigo.

---

## ⚖️ Licencia y Aviso Legal

Este proyecto está licenciado bajo la **GNU Affero General Public License v3.0 (AGPLv3)**.

> **Aviso de Doble Licenciamiento**  
> Este software es **gratuito y de código abierto** bajo la licencia AGPLv3. Esto asegura que cualquier modificación de red que realices deba ser compartida libremente con la comunidad.
> 
> Sin embargo, si eres una empresa o entidad que desea utilizar este código base para lanzar productos **propietarios, comerciales de código cerrado**, o simplemente sin las obligaciones de liberación de código que impone la AGPLv3, **es obligatorio contactar con el desarrollador original (`ferlagod`)**.

Consulta el archivo [LICENSE](LICENSE) completo para más detalles.
