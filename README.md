# moscow-heat-trace-service

## Структура проекта на данный момент
```bash
moscow-heat-trace-service/
│
├── .devcontainer/
│   ├── devcontainer.json
│   ├── docker-compose.yml
│   ├── post-create.sh
│   └── post-start.sh
│
├── src/
│   ├── main/
│   │   ├── java/ru/moscow/heat/
│   │   │   ├── controller/
│   │   │   │   └── HealthController.java
│   │   │   └── HeatTraceServiceApplication.java
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/ru/moscow/heat/
│           └── HeatTraceServiceApplicationTests.java
│
├── target/                         # (gitignored, генерируется Maven)
├── .gitignore
├── LICENSE
├── pom.xml
└── README.md
```
