# OsuCraft — osu! dentro de Minecraft (Fabric 1.21.11)

Clic derecho a un **Bloque de osu!** y se abre el juego dentro de Minecraft.

## Qué trae
- **Dos modos**
  - **Standard**: círculos, sliders (lineales, curvos, Bézier, Catmull) y spinners. Apuntás con el mouse y golpeás con 2 teclas (por defecto `Z` / `X`; también podés usar los botones del mouse).
  - **Mania**: de 1 a 10 carriles, con notas largas (hold).
- **Importar mapas de osu!**: archivos `.osz` (mp3 / ogg / wav). Arrastrá el `.osz` a la ventana del juego, o copialo en `osucraft/import/`.
  Los mapas **Standard / Taiko / Catch se adaptan solos a Mania** (por posición en pantalla), con la cantidad de teclas que elijas. Los mapas Mania nativos se juegan tal cual.
- **Teclas personalizables**: una configuración por cada cantidad de carriles (1K–10K) y otra para Standard.
- **Crear niveles**: generador automático (Escalera, Saltos, Stream, Aleatorio; Mania o Standard) y **grabador**: tocás las teclas al ritmo del metrónomo y se guarda el nivel.
  Los niveles se guardan como `.osu` normales, así que también se pueden abrir en osu!.
- Puntaje, combo, precisión y rango (SS/S/A/B/C/D). Ventanas de juicio y fórmulas de osu!.
- **HP y fallar**: la barra baja con los errores; si llega a 0 perdés (se desactiva con el mod NF).
- **Mods**: EZ, HR (espeja el campo en Standard), HT, DT (música más rápida/lenta), HD y NF. Se eligen en el selector de canciones y cambian el multiplicador de puntaje.
- **Tecla O** para abrir el menú sin el bloque (se cambia o se quita en Controles → osu!).
- Opciones: velocidad de caída, offset de audio, volúmenes, sonido al golpear.

## Cómo conseguir el bloque
Receta: 8 tintes rosas alrededor de 1 bloque musical (note block). También está en la pestaña creativa **Funcionales**.
Comando: `/give @s osucraft:osu_block`

## Cómo compilarlo
Necesitás **JDK 21** y conexión a internet (Gradle baja Minecraft, Fabric y el decodificador de mp3).

```
./gradlew build          # Linux / macOS
gradlew.bat build        # Windows
```
El mod queda en `build/libs/osucraft-1.0.0.jar` (no uses el `-sources`).
Para probarlo directo desde el proyecto: `./gradlew runClient`.

Instalación: poné el `.jar` en tu carpeta `mods` junto con **Fabric Loader** y **Fabric API** para 1.21.11.

## Carpetas (dentro de tu carpeta de Minecraft)
```
osucraft/songs/    mapas ya importados (una carpeta por mapa)
osucraft/import/   dejá acá tus .osz y tocá "Importar"
osucraft/config.properties   teclas y opciones
```

## Controles en partida
- Mania: tus teclas por carril. Standard: mouse + 2 teclas (hold para sliders, mantené y girá para spinners).
- Contadores 300/100/50/MISS a la derecha, barra de HP arriba a la izquierda.
- `Esc` pausa (`R` reiniciar, `Q` salir; al grabar, `G` guarda y sale).

## Notas
- Mapas de Taiko y Catch se juegan adaptados a Mania.
- Si un mapa no tiene audio legible, se juega igual (sin música) y avisa abajo a la izquierda.
- Si pegás siempre tarde o temprano, ajustá el **offset** en Opciones.
- `dev-tests/CoreTest.java` prueba la lógica (parser, motores, importación, configuración) sin Minecraft:
  `javac -d out src/main/java/com/osucraft/core/*.java dev-tests/CoreTest.java && java -cp out CoreTest`
