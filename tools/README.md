# tools/

Utilidades de desarrollo (no forman parte del jar).

## GenTheme.java

Genera las texturas originales del tema de las interfaces (marco de madera, panel
marrón, botón crema/dorado, casilla hundida). Todo procedural.

```bash
javac tools/GenTheme.java -d tools/out
java -cp tools/out GenTheme src/main/resources/assets/realeconomy/textures/gui/theme
```

Salida: `panel.png`, `button.png`, `slot.png` en la carpeta indicada. Ajusta la
paleta o las métricas en el propio archivo y vuelve a ejecutarlo. Si cambias los
tamaños o bordes, actualiza también las constantes de `Theme.java`.
