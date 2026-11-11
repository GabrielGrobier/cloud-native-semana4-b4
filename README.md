# Semana 4 · Demo de API pública detrás de AWS API Gateway

Proyecto mínimo para **Desarrollo Cloud Native (CDY2204), Experiencia 2, Semana 4**.

**Propósito:** demostrar que un backend Java 21 + Spring Boot funciona en un contenedor Docker sobre EC2 con IP pública y, después, proteger **el acceso a través de AWS API Gateway** usando tokens emitidos por Azure AD B2C. El API Gateway se configura **a mano**, no mediante CI/CD.

## Arquitectura

```text
     Usuario / Postman
           |                           Azure AD B2C
           |                            (emite JWT)
           |                                |
           +-- GET /datos + Bearer <JWT> ---+
           |
           v
    AWS API Gateway (HTTP API)
      ruta GET /datos
      autorizador JWT o Lambda (configurado manualmente)
           |
           | Integración HTTP: http://IP_PUBLICA_EC2:8080/datos
           v
    EC2 con IP pública :8080
          Docker
       Java Spring Boot
         /salud
         /datos

    Demostración adicional:
    Navegador -> http://IP_PUBLICA_EC2:8080 (sin JWT, accesible)
```

**IMPORTANTE PARA LA CLASE:** El backend `/datos` es público y **se puede consultar sin JWT mediante la IP**. Por diseño, el autorizador del Gateway **solo protege la URL del Gateway**; **no protege el acceso directo** a EC2. Este proyecto NO debe presentarse como una arquitectura de producción segura. Para proteger realmente el origen, utilizar una integración privada vía VPC Link + ALB/NLB o validar JWT también en el backend. Nunca alojar datos reales/sensibles en esta demo.

## Requisitos

- AWS EC2 Ubuntu/Amazon Linux con Docker y plugin `docker compose` instalados, IP pública y Security Group que permita **TCP 8080**. Para una integración HTTP pública, el tráfico de API Gateway no necesariamente saldrá desde la IP del docente, por lo que **durante esta demostración** puede requerirse abrir 8080 temporalmente a Internet (`0.0.0.0/0`). Eso expone `/datos` directamente; cerrar el puerto al terminar. SSH TCP 22 solo desde una IP administrativa o una alternativa más segura.
- Cuenta Docker Hub para imagen pública o privada (con login).
- Repositorio GitHub con `main` y Actions habilitadas.
- Azure AD B2C creado con una aplicación `cdy2204-api`, scope `datos.leer`, y forma de obtener un **access token** para esa API. No usar un ID token para llamar a `/datos`.
- **Costos:** EC2, almacenamiento y API Gateway pueden generar cargos.

## 1. Ejecutar localmente

```bash
cd demo-api
mvn -B -ntp clean test
mvn -B -ntp spring-boot:run
```

Abrir `http://localhost:8080`, `http://localhost:8080/salud` y `http://localhost:8080/datos`.

**La aplicación no necesita base de datos ni credenciales Azure**. Los datos de `/datos` son de ejemplo (catálogo ficticio), por lo que siempre podrá responder aunque B2C o el Gateway estén fuera de servicio.

## 2. GitHub Actions (adaptado de la Semana 1)

El workflow `.github/workflows/deploy.yml` conserva el esquema:

1. `tests` ejecuta pruebas unitarias con **Java 21 + Maven** en PRs y push a `main`.
2. `docker` construye la imagen `latest` + `sha`, y la publica en **Docker Hub** (solo push a `main` o ejecución manual).
3. `deploy-ec2` copia `compose.yml` por SCP, ejecuta Docker Login, Pull y Up por SSH, y prueba `/salud` (solo despliegues, no PRs).

Configurar estos **GitHub Actions Secrets**:

| Secret | Ejemplo / descripción |
|---|---|
| `DOCKERHUB_USERNAME` | Tu usuario de Docker Hub |
| `DOCKERHUB_TOKEN` | Token de Docker Hub |
| `DOCKER_IMAGE_NAME` | `cdy2204-semana4-demo` (sin prefijo de usuario) |
| `EC2_HOST` | IP pública o DNS de la EC2 |
| `EC2_USER` | `ubuntu` o `ec2-user` |
| `EC2_SSH_KEY` | Contenido completo de la clave privada PEM de SSH |

La instancia EC2 necesita poder ejecutar `docker` sin `sudo` para `EC2_USER`. Confirma que `curl` está instalado en EC2 para el control de salud del pipeline.

### Desplegar

Haz un push a la rama `main`. Actions desplegará el contenedor en la instancia EC2.

Verifica:

```bash
curl -i http://IP_PUBLICA_EC2:8080/salud
curl -i http://IP_PUBLICA_EC2:8080/datos
```

Abre en un navegador `http://IP_PUBLICA_EC2:8080` para mostrar una página visible. Si no responde, revisar `docker compose ps`, Security Group, firewall del sistema y el puerto `8080`.

### Actualizaciones

Cada push a `main` reconstruye y reemplaza automáticamente la imagen. Este proyecto usa el tag `latest` para mantener simple la demostración, aunque la imagen publicada tiene también un tag por commit `sha`.

## 3. Configurar AWS API Gateway a mano

1. AWS Console > API Gateway > **Create API > HTTP API**.
2. Agrega una **HTTP integration** al endpoint público `http://IP_PUBLICA_EC2:8080/datos`.
3. Configura la ruta **GET `/datos`** (el método de integración también debe ser GET).
4. Usa stage `$default` con auto-deploy, o un stage `demo` desplegado manualmente.
5. Prueba **primero sin autorizador** la URL del Gateway: deberá devolver el mismo JSON que EC2.
6. Después configura un autorizador para Azure AD B2C y asígnalo a `GET /datos`.
7. Prueba sin token (debe rechazarse **en el Gateway**); vuelve a probar con un **access token** válido para la API y el scope requerido.

En **Azure AD B2C** la metadata OIDC es **específica del user flow**. Usa la URL `.well-known/openid-configuration` desde el portal de B2C para identificar `issuer`, `jwks_uri`, audiencia y política. **El autorizador nativo JWT del Gateway no es necesariamente compatible con todas las estructuras de issuer / metadata de B2C**; si la validación o el descubrimiento OIDC falla, configura un **Lambda REQUEST authorizer** que verifique firma, `iss`, `aud`, expiración, política (`tfp` / `acr`) y scope. No uses un issuer de Entra ID corporativo para un token B2C.

**No es necesario crear Lambda para el backend**, ya que el backend está en EC2. Una Lambda authorizer, si se usa, es solo para verificar JWT.

### Pruebas comparativas para presentar

| Prueba | URL | Resultado |
|---|---|---|
| 1. EC2 directa | `http://IP_PUBLICA_EC2:8080/datos` | `200 OK`, sin JWT: prueba que existe la aplicación |
| 2. Gateway sin JWT | `https://API_ID.execute-api.REGION.amazonaws.com/datos` | `401 Unauthorized` tras activar el autorizador |
| 3. Gateway con JWT válido | Misma URL + `Authorization: Bearer <access_token>` | `200 OK` y datos de la API |
| 4. EC2 directa de nuevo | `http://IP_PUBLICA_EC2:8080/datos` | Continúa dando `200` sin JWT: muestra el límite de esta arquitectura pública |

> **Cuidado:** si intentas la prueba 3 con un **ID token** o un token para Microsoft Graph, AWS lo debe rechazar por audiencia y/o scope. Solicita un *access token* para tu `cdy2204-api` desde la aplicación cliente registrada en Azure.

## 4. Sugerencia para la sesión

- **Parte A:** mostrar `/` en navegador y `/datos` por IP pública: la aplicación funciona por sí sola.
- **Parte B:** crear API Gateway y conectar `GET /datos` a la IP pública.
- **Parte C:** conectar Azure AD B2C; demostrar con Postman la diferencia entre solicitud sin token y con token.
- **Cierre:** explicar por qué una EC2 accesible desde Internet puede eludir el Gateway y cómo se mejoraría en una arquitectura real.

## 5. Estructura

```text
cloud-native-semana4-demo-ec2/
├── .github/workflows/deploy.yml
├── demo-api/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/...
├── compose.yml
└── README.md
```

Este laboratorio es deliberadamente pequeño: **1 aplicación, 1 contenedor, 2 endpoints JSON y 1 página HTML**. No hay frontend SPA, base de datos, servicio de mensajería ni despliegue automático de API Gateway.
