# Plan de Sprints y Tareas
Este documento desglosa las historias de usuario y sus tareas correspondientes en un plan de Sprints, priorizado para la implementación.

## Sprint 1: El Núcleo del Backend y el Pipeline de Datos (Agosto 2025)
**Objetivo:** Construir la base de la aplicación. Al final de este Sprint, el sistema deberá ser capaz de recolectar datos de GitHub, procesarlos para obtener 2 métricas clave y tener toda la infraestructura de seguridad lista.

---

### HU-1: Iniciar Sesión
Status: Completada
*Como un usuario no autenticado, quiero poder iniciar sesión con mi cuenta de GitHub, para acceder a la aplicación de forma segura.*

- **AC 1.1: Redirección a GitHub para autorización** Status: Completada
    - *Dado que no estoy logueado, cuando hago clic en 'Iniciar Sesión con GitHub', entonces soy redirigido a la página de autorización de GitHub.*
- **AC 1.2: Sesión activa y redirección al dashboard post-autorización** Status: Completada
    - *Dado que he autorizado la aplicación en GitHub, cuando soy redirigido de vuelta, entonces debo ver el dashboard principal y mi sesión debe estar activa.*
- **AC 1.3: Mensaje de error para usuarios no miembros de la organización** Status: Completada
    - *Dado que no soy miembro de la organización configurada, cuando intento iniciar sesión, entonces veo un mensaje de error de 'Acceso Denegado'.*

### HU-2: Cerrar Sesión
Status: Completada
*Como un usuario autenticado, quiero poder cerrar mi sesión, para proteger mi cuenta cuando termine de usar la aplicación.*

- **AC 2.1: Invalidación de sesión y redirección a la página de login** Status: Completada
    - *Dado que estoy logueado, cuando hago clic en 'Cerrar Sesión', entonces mi sesión se invalida y soy redirigido a la página de inicio de sesión.*

### HU-3: Configuración Inicial
Status: Completada
*Como el primer usuario de la aplicación, quiero ser asignado automáticamente como Administrador, para poder realizar la configuración inicial del sistema.*

- **AC 3.1: Asignación automática del rol de Administrador al primer usuario** Status: Completada
    - *Dado que la aplicación no tiene una organización configurada ni administradores, cuando inicio sesión por primera vez, entonces mi usuario es creado con el rol de 'Administrador'.*
- **AC 3.2: Redirección a la página de configuración para el primer administrador** Status: Completada
    - *Dado que soy el primer administrador, después de iniciar sesión, entonces soy redirigido a la página de 'Configuración del Sistema'.*

### HU-17: Implementar un Modelo de Acceso "Cerrado por Defecto" en el Arranque
Status: Completada
*Como administrador del sistema,
necesito que la aplicación bloquee por defecto todos los inicios de sesión, excepto el del administrador inicial designado, cuando aún no he configurado una organización de GitHub,
para garantizar la máxima seguridad desde el primer despliegie y prevenir cualquier registro de usuario no autorizado antes de que el sistema esté completamente configurado.*

- **AC 17.1: Implementar y Probar el Bloqueo de Usuarios No Autorizados** Status: Completada
    - *Dado que el sistema está en su estado de arranque inicial (no hay ADMIN en la BD). Y la variable dora.github.organization-name NO está definida. Cuando un usuario que NO es el dora.initial-admin-username intenta iniciar sesión. Entonces el acceso debe ser denegado y no se debe crear ningún registro de usuario para él.*
- **AC 17.2: Asignación del rol por defecto 'Desarrollador'** Status: Completada
    - *Dado que el sistema está en su estado de arranque inicial. Y la variable dora.github.organization-name NO está definida. cuando el usuario que SÍ es el dora.initial-admin-username intenta iniciar sesión. entonces debe ser creado exitosamente con el rol de ADMIN.*
- **AC 17.3: Asignación del rol por defecto 'Desarrollador'** Status: Completada
  - *Dado que un ADMIN ya existe en el sistema. y la variable dora.github.organization-name sigue sin estar definida. cuando un nuevo usuario (que no es el admin) intenta iniciar sesión. entonces su acceso debe ser denegado.*

### HU-15: Estructura Base del Frontend
Status: Completada
*Como desarrollador, quiero una estructura de proyecto React con ruteo y layouts, para tener una base sólida sobre la cual construir la UI.*

- **AC 15.1: Mostrar LoginPage para usuarios no autenticados**
- Status: Completada
    - *Dado que la aplicación carga, cuando un usuario no está autenticado, entonces se le muestra la LoginPage.*
- **AC 15.2: Mostrar AuthenticatedLayout para usuarios autenticados**
- Status: Completada
    - *Dado que un usuario está autenticado, cuando navega por la aplicación, entonces ve el AuthenticatedLayout (header y sidebar) de forma persistente.*

### HU-19: Crear Página de Inicio de Sesión
Status: Completada
*Como usuario no autenticado, quiero ver una página de bienvenida simple que me invite a iniciar sesión con mi cuenta de GitHub para poder acceder a la aplicación.*

- **AC 19.1: Mostrar Página de Inicio**
- Status: Completada
    - **DADO** que no he iniciado sesión.
    - **CUANDO** visito la raíz de la aplicación (`/`).
    - **ENTONCES** se me presenta una página de bienvenida.
- **AC 19.2: Botón de Login Funcional**
- Status: Completada
    - **DADO** que estoy en la página de bienvenida.
    - **CUANDO** hago clic en el botón "Iniciar Sesión con GitHub".
    - **ENTONCES** soy redirigido al flujo de autorización de GitHub.

### HU-20: Crear Página Principal (Home) para Usuarios Autenticados
Status: Completada
*Como usuario que ha iniciado sesión, quiero ser dirigido a una página principal o "Home" donde pueda ver contenido exclusivo y acceder a acciones como "Cerrar Sesión".*

- **AC 20.1: Redirección a la Página Principal**
- Status: Completada
    - **DADO** que he completado el inicio de sesión con éxito.
    - **CUANDO** soy redirigido por el sistema.
    - **ENTONCES** aterrizo en una URL protegida (ej. `/home` o `/dashboard`).
- **AC 20.2: Contenido y Botón de Logout**
- Status: Completada
    - **DADO** que estoy en la página principal.
    - **CUANDO** observo el contenido.
    - **ENTONCES** veo un mensaje de bienvenida simple y el botón "Cerrar Sesión".

### HU-18: Interfaz de Usuario para Cerrar Sesión
Status: Completada
Como usuario autenticado, quiero poder cerrar mi sesión de forma segura, para proteger mi cuenta de accesos no autorizados, especialmente en dispositivos

- **AC 18.1: Visibilidad del Botón de Logout**
- Status: Completada
    - **DADO** que he iniciado sesión correctamente.
    - **CUANDO** navego por la aplicación.
    - **ENTONCES** debo ver un elemento claramente identificable (botón o enlace) con el texto "Cerrar Sesión" en un lugar predecible (ej. en la barra de navegación o en un menú de perfil).
- **AC 18.2: Funcionalidad del Botón de Logout**
- Status: Completada
    - **DADO** que estoy viendo el botón "Cerrar Sesión".
    - **CUANDO** hago clic en él.
    - **ENTONCES** soy redirigido inmediatamente a la página de inicio (`/`).
- **AC 18.3: Verificación de Sesión Terminada**
- Status: Completada
    - **DADO** que he hecho clic en "Cerrar Sesión" y he sido redirigido.
    - **CUANDO** intento acceder a una ruta protegida que antes podía ver.
    - **ENTONCES** se me debe denegar el acceso y ser redirigido a la página de inicio de sesión.

### HU-10: Recolectar Datos de GitHub
*Como el sistema, quiero conectarme a la API de GitHub de forma periódica, para recolectar eventos de PRs, commits y deployments.*

- **AC 10.1:** 
- Status: Completada
Dado que la aplicación se inicia y el esquema de la base de datos es gestionado por JPA/Hibernate.
  Cuando un desarrollador inspecciona el esquema de la base de datos.
  Entonces las tablas REPOSITORY_CONFIG y SYNC_STATUS deben existir con sus columnas correctamente definidas, listas para ser usadas por los servicios de sincronización. Status: Completada
- **AC 10.2:** 
- Status: Completada
  - Dado que el framework de sincronización está implementado y existe una configuración de repositorio.
    Cuando se ejecuta el CommitSyncService.
    Entonces los nuevos commits del repositorio de GitHub se guardan en la tabla COMMIT, y la tabla SYNC_STATUS se actualiza con la nueva fecha de lastSuccessfulRun para el job "COMMIT_SYNC". Status: Completada
- **AC 10.3:** Dado que el framework de sincronización está implementado.
  Cuando se ejecuta el PullRequestSyncService.
  Entonces los nuevos Pull Requests del repositorio de GitHub se guardan en la base de datos, y la tabla SYNC_STATUS se actualiza para el job "PULL_REQUEST_SYNC".
- **AC 10.4:** Dado que el framework está implementado y la aplicación está configurada con el nombre del workflow de despliegue.
  Cuando se ejecuta el DeploymentSyncService.
  Entonces las nuevas ejecuciones exitosas de dicho workflow se guardan como registros de Deployment, y la tabla SYNC_STATUS se actualiza para el job "DEPLOYMENT_SYNC".
- **AC 10.5:** Implementar paginación en la recolección de commits
- Status: Completada
  Dado que un repositorio ha tenido más de 100 commits desde la última sincronización. Cuando se ejecuta el CommitSyncService. Entonces el GithubClientImpl debe realizar múltiples llamadas a la API de GitHub, siguiendo los enlaces de paginación (rel="next"). Y todos los commits nuevos (más de 100) deben ser guardados correctamente en la base de datos.

### HU-11: Procesar Métricas de Velocidad
*Como el sistema, quiero procesar los datos de GitHub, para calcular la Frecuencia de Despliegue y el Tiempo de Espera para Cambios.*

- **AC 11.1: Cálculo y almacenamiento de la Frecuencia de Despliegue**
    - *Dado que hay eventos de despliegue y commits en la base de datos, cuando el job de procesamiento se ejecuta, entonces se calcula y guarda correctamente la métrica de Frecuencia de Despliegue.*
- **AC 11.2: Cálculo y almacenamiento del Tiempo de Espera para Cambios**
    - *Dado que hay eventos de commits y PRs, cuando el job de procesamiento se ejecuta, entonces se calcula y guarda correctamente la métrica de Tiempo de Espera para Cambios.*

### HU-5: Gestionar Roles
*Como Administrador, quiero poder ver una lista de todos los usuarios pertenecientes a la organización y asignarles roles, para controlar el acceso a la aplicación.*

- **AC 5.1: Sincronización y visualización de miembros de la organización**
    - *Dado que estoy en la página de gestión de usuarios, cuando la página carga, entonces veo una tabla con todos los miembros de la organización de GitHub sincronizados.*
- **AC 5.2: Asignación del rol por defecto 'Desarrollador'**
    - *Dado que estoy viendo la lista de usuarios, entonces cada usuario que no tiene un rol explícitamente asignado se muestra con el rol por defecto 'Desarrollador'.*

---

## Sprint 2: Integraciones Completas y Notificaciones (Septiembre 2025)
**Objetivo:** Expandir el pipeline de datos para incluir todas las fuentes y métricas, y construir el sistema de alertas. Al final de este Sprint, el backend será completamente funcional.

---

### HU-12: Recolectar Datos de Jira
*Como el sistema, quiero conectarme a la API de Jira de forma periódica, para enriquecer los datos de los commits.*

- **AC 12.1: Obtención de datos de tickets de Jira**
    - *Dado que el job de recolección se ejecuta, cuando se conecta a la API de Jira, entonces obtiene los datos de los tickets mencionados en los commits.*
- **AC 12.2: Prevención de inserción de eventos duplicados de Jira**
    - *Dado que se obtiene un evento de Jira, cuando se intenta guardar en la base de datos, entonces se previene la inserción de duplicados.*

### HU-13: Recolectar Datos de DataDog
*Como el sistema, quiero conectarme a la API de DataDog de forma periódica, para recolectar eventos de incidentes.*

- **AC 13.1: Obtención y almacenamiento de incidentes de DataDog**
    - *Dado que el job de recolección se ejecuta, cuando se conecta a la API de DataDog, entonces obtiene los incidentes nuevos y los guarda en la base de datos.*
- **AC 13.2: Prevención de inserción de incidentes duplicados de DataDog**
    - *Dado que se obtiene un incidente de DataDog, cuando se intenta guardar en la base de datos, entonces se previene la inserción de duplicados.*

### HU-14: Procesar Métricas de Estabilidad
*Como el sistema, quiero procesar los datos de despliegues e incidentes, para calcular la Tasa de Fallo de Cambio y el Tiempo Medio de Recuperación.*

- **AC 14.1: Cálculo y almacenamiento de la Tasa de Fallo de Cambio**
    - *Dado que hay eventos de despliegues e incidentes, cuando el job de procesamiento se ejecuta, entonces se calcula y guarda correctamente la métrica de Tasa de Fallo de Cambio.*
- **AC 14.2: Cálculo y almacenamiento del Tiempo Medio de Recuperación**
    - *Dado que hay eventos de incidentes, cuando el job de procesamiento se ejecuta, entonces se calcula y guarda correctamente la métrica de Tiempo Medio de Recuperación.*

### HU-9: Enviar Notificación de Alerta
*Como el sistema, quiero enviar una notificación por correo electrónico, para alertar a los usuarios relevantes sobre cambios significativos en el rendimiento.*

- **AC 9.1: Disparo de evento de notificación al exceder umbral**
    - *Dado que el module-processor ha calculado una nueva métrica, cuando el valor de esta métrica excede un umbral predefinido, entonces se dispara un evento de notificación.*
- **AC 9.2: Envío de correo electrónico al rol correspondiente**
    - *Dado que se ha disparado un evento de notificación, cuando el module-notifications lo recibe, entonces se envía un correo electrónico al Tech Lead o Engineering Manager responsable.*

### HU-4: Gestionar Conexiones (Backend)
*Como Administrador, quiero que el sistema pueda usar las credenciales de conexión, para que el sistema pueda recolectar datos.*

- **AC 4.1: Lectura de claves de API desde variables de entorno**
    - *El sistema lee correctamente las variables de entorno para las claves de API.*
- **AC 4.2: Descifrado de secretos con Jasypt**
    - *El sistema usa Jasypt para descifrar los secretos necesarios.*

---

## Sprint 3: Frontend y Visualización (Octubre 2025)
**Objetivo:** Construir la interfaz de usuario completa, integrar los dashboards de Grafana y entregar la aplicación final.

---



### HU-16: Página de Administración de Roles
*Como Administrador, quiero una interfaz para gestionar los roles de los usuarios, para controlar los permisos de la aplicación.*

- **AC 16.1: Visualización de la tabla de usuarios para Administradores**
    - *Dado que he iniciado sesión como Administrador, cuando navego a la página de administración, entonces veo una tabla con los usuarios de la organización.*
- **AC 16.2: Actualización de rol de usuario vía API**
    - *Dado que estoy viendo la tabla de usuarios, cuando cambio el rol de un usuario, entonces se realiza una llamada a la API y la UI se actualiza con el nuevo rol.*

### HU-6: Dashboard de Engineering Manager
*Como Engineering Manager, quiero ver un dashboard con las métricas DORA agregadas a nivel de toda la organización, para entender el rendimiento general de la ingeniería.*

- **AC 6.1: Vista de datos de toda la organización por defecto**
    - *Dado que he iniciado sesión como Engineering Manager, cuando accedo al dashboard, entonces los gráficos muestran por defecto los datos de todos los equipos.*
- **AC 6.2: Filtro de datos por equipo**
    - *Dado que estoy viendo el dashboard, cuando uso el filtro de equipo y selecciono 'Equipo Alfa', entonces todos los gráficos se actualizan para mostrar solo los datos del 'Equipo Alfa'.*

### HU-7: Dashboard de Tech Lead
*Como Tech Lead, quiero ver un dashboard con las métricas DORA específicas de mi equipo y sus repositorios, para monitorear la salud y la eficiencia de mi equipo.*

- **AC 7.1: Vista de datos restringida al equipo del Tech Lead**
    - *Dado que he iniciado sesión como Tech Lead del 'Equipo Beta', cuando accedo al dashboard, entonces solo veo los datos del 'Equipo Beta'.*
- **AC 7.2: Ausencia de filtro para otros equipos**
    - *Dado que soy Tech Lead, cuando veo el dashboard, entonces no tengo la opción de filtrar por otros equipos.*

### HU-8: Dashboard de Desarrollador
*Como Desarrollador, quiero ver un dashboard con las métricas DORA de los repositorios en los que contribuyo, para entender el impacto de mi trabajo en el ciclo de entrega.*

- **AC 8.1: Vista de datos restringida a los repositorios del Desarrollador**
    - *Dado que he iniciado sesión como Desarrollador, cuando accedo al dashboard, entonces los gráficos muestran por defecto los datos de todos los repositorios en los que he hecho commits.*