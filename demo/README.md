# Cómo probar este plugin

Este plugin dibuja un mapa de cómo las distintas partes de un
proyecto (llamadas "módulos") dependen unas de otras, y avisa si hay
un problema circular (A necesita a B, y B necesita a A — un lío que
puede causar errores).

## Qué hacer

1. Este proyecto de prueba ya tiene 3 partes: `orders-service`,
   `shipping-service`, y `common-utils`.
2. Buscá en el panel lateral (usualmente a la derecha o abajo) una
   pestaña o ícono con el logo de Gap Hunter Labs — el nombre real es
   **"Module Dependencies"**. Hacé click ahí para abrirlo.
3. Si el panel aparece vacío, buscá un botón de "actualizar" o
   "refresh" dentro de ese mismo panel y hacé click — este plugin no
   se actualiza solo, hay que pedirle que revise.

## Qué deberías ver

- Una lista con las 3 partes del proyecto.
- Una sección en color rojo que dice algo como "Cycles detected" (o
  "ciclos detectados"), mostrando que `orders-service` y
  `shipping-service` dependen una de la otra — ese es el problema que
  el plugin tiene que encontrar.
- `common-utils` no debería aparecer en ningún problema — es una
  parte "tranquila" que no depende de nadie.

## Si algo no se ve así

Sacá la captura igual, y avisame qué no coincide con lo de arriba.
