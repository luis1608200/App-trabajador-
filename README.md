# App Trabajador · Android

Registro personal de jornadas e ingresos sin internet. Proyecto independiente, sin conexiones con LUMARU ni Akasha.

## Beta para Redmi

La APK de prueba se identifica como `pe.apptrabajador.beta` y aparece como **App Trabajador · Prueba**. Se instala por separado de la aplicación original `pe.apptrabajador`. Es una beta; la instalación y el comportamiento final en el Redmi Note 15 Pro físico quedan pendientes de validar.

El código original se conserva en la rama `backup/antes-version-offline-2026-10-25`, commit `db45ef3ccf192007381c78a42d2bc8f24d5e9efe`. Los cambios están en `codex/app-trabajador-offline` y en el PR #3. No se ha reemplazado `main`.

## Uso

1. En **Ajustes**, configura sueldo por hora, jornal o sueldo mensual, jornada normal, días de referencia y descuentos. Activa propinas, comisiones y bonos si aplican. Guarda la configuración.
2. En **Inicio**, toca entrada, salida o descanso, o **Registrar jornada**. Elige las horas, el descanso en minutos, ingresos adicionales y notas. También puedes marcar entrada y salida con la hora actual.
3. Para turnos nocturnos activa **Salida al día siguiente**. La jornada corresponde a la fecha de entrada.
4. En **Calendario**, toca una fecha para registrar o editar trabajo, descanso o ausencia.
5. En **Historial**, revisa, edita o elimina con confirmación. Puedes deshacer la última eliminación mientras se mantiene la sesión.
6. **Exportar reporte PDF** permite elegir semana, quincena, mes o rango personalizado. Elige una carpeta local para exportar sin internet.

El editor guarda un borrador local al cambiar de pestaña o recrear la pantalla. **Guardar registro** confirma los cambios; **Descartar borrador** conserva el registro anterior si existía.

## Implementado

- Tarjetas blancas, degradado azul, iconos vectoriales y cuatro pestañas basados en el boceto original.
- Márgenes para barra de estado, recorte de cámara, navegación y teclado mediante `WindowInsets`.
- Entrada/salida y descanso editables; una jornada por fecha de inicio y turnos de hasta 24 horas.
- Historial SQLite privado y persistente, edición, eliminación y calendario real con años bisiestos.
- Cálculo de horas normales, dos tramos de extras, variables, descuentos y neto estimado.
- Configuración guardada y copia de las reglas utilizadas en cada registro histórico.
- Gráficos de cuatro meses, comparación mensual y resúmenes construidos con registros reales.
- PDF paginado con resumen y desglose diario de horas, tarifas, ingresos y descuentos.
- Sin permiso `INTERNET`, cuentas, anuncios o rastreadores. No hay datos de demostración en la APK.

## Reglas del cálculo

Es una estimación de las jornadas registradas, no una planilla de remuneración automática.

- Tiempo efectivo = salida - entrada - descanso. La opción nocturna añade un día a la salida.
- Normales: hasta la jornada diaria configurada. Extras: el exceso, con una sobretasa para las primeras 2 horas y otra para el resto. Las tasas iniciales de 25% y 35% son editables.
- Tarifa: importe por hora, jornal / horas normales, o sueldo mensual / días de referencia / horas normales.
- AFP/ONP, seguro y otros porcentajes se aplican a normal + extras. Se pueden incluir variables en esa base.
- Descuento fijo mensual de referencia: importe / días de referencia, descontado una vez por cada jornada trabajada. Exportar no vuelve a descontarlo.
- Los descuentos o adelantos específicos de un día se suman a los demás descuentos.
- Los componentes diarios se redondean a céntimos con `BigDecimal`, luego se suman. Un neto negativo no se oculta.
- Cambiar ajustes no cambia registros previos. Al editar un registro, recalcular con ajustes actuales es una opción explícita.

## Limitaciones

- No calcula automáticamente feriados, descansos remunerados, CTS, gratificaciones, vacaciones, impuestos, aportes del empleador ni el régimen de construcción civil. Descansos y ausencias no generan ingresos o descuentos automáticos.
- El sueldo mensual deriva una tarifa: solo se suman horas registradas, no se garantiza el sueldo completo del mes.
- Una jornada por fecha de entrada; no admite varios turnos separados con la misma fecha.
- Sin sincronización, conexión empresarial o importación/restauración de datos. El PDF es un reporte, no una copia restaurable. Desinstalar o borrar los datos de la beta elimina sus registros.
- Firma de depuración, no firma de producción ni publicación en Google Play. El workflow conserva una clave de prueba en caché; si se pierde esa caché, hay que gestionar la firma y los datos antes de actualizar.
- Falta validar en el Redmi físico el diseño, tamaño de texto, teclado, gestos de HyperOS y selector de archivos.

## Compilación y validación

JDK 17, Gradle 8.7 y Android SDK 35. La primera compilación descarga herramientas y dependencias; ejecutar la app no requiere internet.

```sh
gradle testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
gradle connectedDebugAndroidTest
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

El workflow **Build and verify Android** comprueba cálculos, Lint y compilación, y ejecuta pruebas instrumentadas en Android 8/API 26 y Android 15/API 35. Publica APK, informes y capturas de pantalla/PDF. Revisa el resultado del commit concreto; generar una APK no equivale a pasar las pruebas de dispositivo.

En el commit `09f15cf6ed60021e7a811324f8ed5f654d23ec12` pasaron las 16 pruebas de cálculo, Lint sin errores y las 4 pruebas instrumentadas en cada API. El workflow marcó fallo porque no pudo recoger capturas después de los tests; el siguiente cambio corrige y verifica esa recogida. La revisión final está en [docs/VALIDACION.md](docs/VALIDACION.md), una vez disponible.

## Código

- `Payroll.java`: cálculo y validación independientes de Android.
- `Storage.java`: SQLite, preferencias y serialización versionada.
- `MainActivity.java`: formularios, calendario, navegación y destino del PDF.
- `IconView.java` / `IncomeChart.java`: iconos y gráficos nativos.
- `PdfReport.java`: reporte y paginación.
- `app/src/test` / `app/src/androidTest`: pruebas unitarias e instrumentadas.
