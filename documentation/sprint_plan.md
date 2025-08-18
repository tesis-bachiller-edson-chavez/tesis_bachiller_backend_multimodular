# Plan de Sprints y Tareas
Este documento desglosa las historias de usuario y sus tareas correspondientes en un plan de Sprints, priorizado para la implementación.

## Sprint 1: El Núcleo del Backend y el Pipeline de Datos (Agosto 2025)
**Objetivo:** Construir la base de la aplicación. Al final de este Sprint, el sistema deberá ser capaz de recolectar datos de GitHub, procesarlos para obtener 2 métricas clave y tener toda la infraestructura de seguridad lista.

---

### HU-1: Iniciar Sesión
*Como un usuario no autenticado, quiero poder iniciar sesión con mi cuenta de GitHub, para acceder a la aplicación de forma segura.*

- **AC 1.1: Redirección a GitHub para autorización**
    - *Dado que no estoy logueado, cuando hago clic en 'Iniciar Sesión con GitHub', entonces soy redirigido a la página de autorización de GitHub.*
- **AC 1.2: Sesión activa y redirección al dashboard post-autorización**
    - *Dado que he autorizado la aplicación en GitHub, cuando soy redirigido de vuelta, entonces debo ver el dashboard principal y mi sesión debe estar activa.*
- **AC 1.3: Mensaje de error para usuarios no miembros de la organización**
    - *Dado que no soy miembro de la organización configurada, cuando intento iniciar sesión, entonces veo un mensaje de error de 'Acceso Denegado'.*

### HU-2: Cerrar Sesión
*Como un usuario autenticado, quiero poder cerrar mi sesión, para proteger mi cuenta cuando termine de usar la aplicación.*

- **AC 2.1: Invalidación de sesión y redirección a la página de login**
    - *Dado que estoy logueado, cuando hago clic en 'Cerrar Sesión', entonces mi sesión se invalida y soy redirigido a la página de inicio de sesión.*

### HU-3: Configuración Inicial
*Como el primer usuario de la aplicación, quiero ser asignado automáticamente como Administrador, para poder realizar la configuración inicial del sistema.*

- **AC 3.1: Asignación automática del rol de Administrador al primer usuario**
    - *Dado que la aplicación no tiene una organización configurada ni administradores, cuando inicio sesión por primera vez, entonces mi usuario es creado con el rol de 'Administrador'.*
- **AC 3.2: Redirección a la página de configuración para el primer administrador**
    - *Dado que soy el primer administrador, después de iniciar sesión, entonces soy redirigido a la página de 'Configuración del Sistema'.*

### HU-10: Recolectar Datos de GitHub
*Como el sistema, quiero conectarme a la API de GitHub de forma periódica, para recolectar eventos de PRs, commits y deployments.*

- **AC 10.1: Obtención de eventos nuevos desde la última ejecución**
    - *Dado que el job de recolección se ejecuta, cuando se conecta a la API de GitHub, entonces obtiene los eventos nuevos desde la última ejecución.*
- **AC 10.2: Prevención de inserción de eventos duplicados**
    - *Dado que se obtiene un evento nuevo, cuando se intenta guardar en la base de datos, entonces se previene la inserción de duplicados.*

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

### HU-15: Estructura Base del Frontend
*Como desarrollador, quiero una estructura de proyecto React con ruteo y layouts, para tener una base sólida sobre la cual construir la UI.*

- **AC 15.1: Mostrar LoginPage para usuarios no autenticados**
    - *Dado que la aplicación carga, cuando un usuario no está autenticado, entonces se le muestra la LoginPage.*
- **AC 15.2: Mostrar AuthenticatedLayout para usuarios autenticados**
    - *Dado que un usuario está autenticado, cuando navega por la aplicación, entonces ve el AuthenticatedLayout (header y sidebar) de forma persistente.*

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