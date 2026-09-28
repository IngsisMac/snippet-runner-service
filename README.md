# snippet-runner-service

Microservicio del TP2 (**Snippet Playground**) responsable del aislamiento de la ejecución, validación, formateo y linteo de código PrintScript.

## Responsabilidades

- Único componente del sistema que posee el JAR de PrintScript (`com.printscript:runner`) en su classpath (**Decisiones D6 y D7**).
- Fachada anticorrupción `PrintScriptFacade` con adaptadores de I/O:
  - `QueuedInputSource`: resolución de `readInput` sobre cola de entradas por caso de prueba (**Decisión D3**).
  - `EnvSource.DENY`: seguridad y aislamiento ante variables de entorno del host (**Decisión D3**).
  - `BoundedOutputEmitter`: cota máxima de 10.000 líneas para evitar desbordes de memoria (**Decisión D2**).
- Endpoints REST internos para ejecución sincrónica y evaluación de casos de prueba.

## Tecnologías

- **Kotlin 2.0** + **Java 21**.
- **Spring Boot 3.3** (Web, Validation, Security).
- **PrintScript Runner** (`1.0.X` publicado en Maven Local / GitHub Packages).
- **SpringDoc OpenAPI 3** (Swagger UI).

## Ejecución Local

```bash
./gradlew bootRun
```

Para correr las pruebas unitarias y de integración:

```bash
./gradlew check
```
