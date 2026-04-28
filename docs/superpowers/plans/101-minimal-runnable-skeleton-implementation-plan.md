# 最小可运行工程骨架 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立 `CMP` 的最小可运行工程骨架，使后端、前端、本地编排和仓库级质量验证入口都能被后续底座任务复用。

**Architecture:** 工程由一个 `Spring Boot` 后端应用、一个 `React SPA` 前端应用、一份 `Docker Compose` 本地编排和四个仓库级验证脚本组成。后端只暴露工程健康检查，前端只展示运行状态并读取后端健康端点，本地依赖只启动 `MySQL` 与 `Redis` 环境入口。该骨架严禁提前实现身份、组织、权限、Agent、集成、合同、文档、流程等业务对象。

**Tech Stack:** `Java 21`、`Spring Boot`、`Maven`、`spring-boot-starter-actuator`、`@SpringBootTest`、`MockMvc`、`React SPA`、`TypeScript`、`Vite`、`pnpm`、`Tailwind CSS v4`、`@tailwindcss/vite`、`Vitest`、`Testing Library`、`Docker Compose`、`MySQL`、`Redis`。

---

## 1. 输入与范围

### 1.1 必读输入

- `docs/superpowers/specs/101-minimal-runnable-skeleton-design.md`
- `docs/superpowers/specs/102-cmp-implementation-execution-spec.md`
- `docs/superpowers/plans/102-01-batch-1-foundations-implementation-plan.md`

### 1.2 范围边界

- 本计划只创建工程入口、测试入口、质量验证入口和本地运行入口。
- 本计划不得新增用户、组织、角色、菜单权限、功能权限、数据权限、授权判定、审计业务对象。
- 本计划不得新增 `AgentTask`、`AgentRun`、`QueryEngine`、工具调用、模型调用、`Harness Kernel` 业务闭环。
- 本计划不得新增入站、出站、回调、绑定、补偿、对账、原始报文治理对象。
- 本计划不得新增合同、业务文稿、业务流程、签章、履约、归档、智能应用功能。
- 本计划不得创建正式业务表结构、业务迁移脚本或可被后续业务依赖的进程内状态。

## 2. 文件结构与职责

- `backend/pom.xml`：后端 Maven 工程声明，固定 `Java 21`、`Spring Boot`、Actuator 和测试依赖。
- `backend/src/main/java/com/cmp/CmpApplication.java`：后端应用启动入口。
- `backend/src/main/java/com/cmp/platform/health/HealthCorsConfig.java`：只为前端本地联调开放健康端点跨域。
- `backend/src/main/resources/application.yml`：后端端口、应用名和 Actuator 健康端点配置。
- `backend/src/test/java/com/cmp/CmpApplicationTests.java`：应用上下文加载测试。
- `backend/src/test/java/com/cmp/platform/health/ActuatorHealthEndpointTests.java`：健康端点 HTTP 测试。
- `backend/Dockerfile`：后端容器构建入口。
- `frontend/package.json`：前端 `pnpm` 脚本和依赖声明。
- `frontend/vite.config.ts`：`Vite`、`React`、`Tailwind CSS v4` 和测试环境配置。
- `frontend/index.html`：前端 HTML 入口。
- `frontend/src/main.tsx`：React 挂载入口。
- `frontend/src/App.tsx`：最小运行页与后端健康状态读取。
- `frontend/src/App.test.tsx`：最小页面渲染测试。
- `frontend/src/index.css`：Tailwind CSS v4 入口与页面基础样式。
- `frontend/src/vite-env.d.ts`：Vite 类型声明。
- `frontend/tsconfig.json`、`frontend/tsconfig.node.json`：前端 TypeScript 配置。
- `frontend/eslint.config.js`：前端 lint 入口。
- `frontend/Dockerfile`：前端开发服务容器入口。
- `docker-compose.yml`：本地 `backend`、`frontend`、`mysql`、`redis` 编排入口。
- `scripts/verify-backend.sh`：后端测试与构建验证入口。
- `scripts/verify-frontend.sh`：前端安装、lint、测试和构建验证入口。
- `scripts/verify-local-stack.sh`：本地 Compose 启动与健康检查验证入口。
- `scripts/verify-all.sh`：仓库级总验证入口。

## 3. 可执行任务清单

### Task 1: 后端 Maven/Spring Boot 最小工程与健康检查测试

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/cmp/CmpApplication.java`
- Create: `backend/src/main/java/com/cmp/platform/health/HealthCorsConfig.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/test/java/com/cmp/CmpApplicationTests.java`
- Create: `backend/src/test/java/com/cmp/platform/health/ActuatorHealthEndpointTests.java`
- Create: `backend/Dockerfile`
- Test: `backend/src/test/java/com/cmp/CmpApplicationTests.java`
- Test: `backend/src/test/java/com/cmp/platform/health/ActuatorHealthEndpointTests.java`

- [ ] **Step 1: 创建失败测试文件**

创建 `backend/src/test/java/com/cmp/CmpApplicationTests.java`：

```java
package com.cmp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CmpApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

创建 `backend/src/test/java/com/cmp/platform/health/ActuatorHealthEndpointTests.java`：

```java
package com.cmp.platform.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ActuatorHealthEndpointTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
```

- [ ] **Step 2: 运行后端测试并确认失败**

Run: `cd backend && mvn test`

Expected: 命令失败，错误包含缺少 `pom.xml`、缺少 `CmpApplication` 或无法加载 Spring Boot 测试上下文中的至少一项。

- [ ] **Step 3: 创建后端 Maven 工程声明**

创建 `backend/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>com.cmp</groupId>
    <artifactId>cmp-backend</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <name>cmp-backend</name>
    <description>CMP minimal backend skeleton</description>

    <properties>
        <java.version>21</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 4: 创建后端最小应用入口与配置**

创建 `backend/src/main/java/com/cmp/CmpApplication.java`：

```java
package com.cmp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CmpApplication {

    public static void main(String[] args) {
        SpringApplication.run(CmpApplication.class, args);
    }
}
```

创建 `backend/src/main/java/com/cmp/platform/health/HealthCorsConfig.java`：

```java
package com.cmp.platform.health;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class HealthCorsConfig {

    @Bean
    WebMvcConfigurer healthEndpointCorsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/actuator/health")
                        .allowedOrigins("http://localhost:5173")
                        .allowedMethods("GET");
            }
        };
    }
}
```

创建 `backend/src/main/resources/application.yml`：

```yaml
spring:
  application:
    name: cmp-backend

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      probes:
        enabled: true
```

- [ ] **Step 5: 创建后端 Dockerfile**

创建 `backend/Dockerfile`：

```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN ./mvnw -q -DskipTests package || mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/cmp-backend-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 6: 运行后端测试并确认通过**

Run: `cd backend && mvn test`

Expected: 命令成功，输出包含 `BUILD SUCCESS`，两个测试类均通过。

- [ ] **Step 7: 运行后端打包验证**

Run: `cd backend && mvn -q -DskipTests package`

Expected: 命令成功，生成 `backend/target/cmp-backend-0.1.0-SNAPSHOT.jar`。

- [ ] **Step 8: 提交建议**

```bash
git add backend
git commit -m "feat: add minimal Spring Boot backend skeleton"
```

### Task 2: 前端 Vite/React/Tailwind 最小工程与渲染测试

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/pnpm-lock.yaml`
- Create: `frontend/index.html`
- Create: `frontend/vite.config.ts`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.node.json`
- Create: `frontend/eslint.config.js`
- Create: `frontend/src/main.tsx`
- Create: `frontend/src/App.tsx`
- Create: `frontend/src/App.test.tsx`
- Create: `frontend/src/index.css`
- Create: `frontend/src/vite-env.d.ts`
- Create: `frontend/Dockerfile`
- Test: `frontend/src/App.test.tsx`

- [ ] **Step 1: 创建前端失败测试文件**

创建 `frontend/src/App.test.tsx`：

```tsx
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import App from './App';

describe('App', () => {
  it('renders the minimal CMP skeleton page', () => {
    render(<App />);

    expect(screen.getByRole('heading', { name: 'CMP 工程骨架已启动' })).toBeInTheDocument();
    expect(screen.getByText('后端健康检查')).toBeInTheDocument();
    expect(screen.getByText('/actuator/health')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: 运行前端测试并确认失败**

Run: `cd frontend && pnpm test -- --run`

Expected: 命令失败，错误包含缺少 `package.json`、缺少 `App.tsx` 或无法解析测试依赖中的至少一项。

- [ ] **Step 3: 创建前端 package 与工具配置**

创建 `frontend/package.json`：

```json
{
  "name": "cmp-frontend",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "lint": "eslint .",
    "test": "vitest",
    "preview": "vite preview"
  },
  "dependencies": {
    "@tailwindcss/vite": "^4.0.0",
    "@vitejs/plugin-react": "^4.3.3",
    "tailwindcss": "^4.0.0",
    "vite": "^5.4.10",
    "react": "^18.3.1",
    "react-dom": "^18.3.1"
  },
  "devDependencies": {
    "@eslint/js": "^9.13.0",
    "@testing-library/jest-dom": "^6.6.3",
    "@testing-library/react": "^16.0.1",
    "@testing-library/user-event": "^14.5.2",
    "@types/react": "^18.3.11",
    "@types/react-dom": "^18.3.1",
    "eslint": "^9.13.0",
    "eslint-plugin-react-hooks": "^5.0.0",
    "eslint-plugin-react-refresh": "^0.4.14",
    "globals": "^15.11.0",
    "jsdom": "^25.0.1",
    "typescript": "^5.6.3",
    "typescript-eslint": "^8.11.0",
    "vitest": "^2.1.4"
  }
}
```

创建 `frontend/vite.config.ts`：

```ts
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    host: '0.0.0.0',
    port: 5173,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test-setup.ts'],
  },
});
```

创建 `frontend/tsconfig.json`：

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["DOM", "DOM.Iterable", "ES2020"],
    "allowJs": false,
    "skipLibCheck": true,
    "esModuleInterop": true,
    "allowSyntheticDefaultImports": true,
    "strict": true,
    "forceConsistentCasingInFileNames": true,
    "module": "ESNext",
    "moduleResolution": "Node",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx"
  },
  "include": ["src"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

创建 `frontend/tsconfig.node.json`：

```json
{
  "compilerOptions": {
    "composite": true,
    "skipLibCheck": true,
    "module": "ESNext",
    "moduleResolution": "Node",
    "allowSyntheticDefaultImports": true
  },
  "include": ["vite.config.ts", "eslint.config.js"]
}
```

创建 `frontend/eslint.config.js`：

```js
import js from '@eslint/js';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist'] },
  {
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
    },
  },
);
```

- [ ] **Step 4: 创建前端应用文件**

创建 `frontend/index.html`：

```html
<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>CMP</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

创建 `frontend/src/main.tsx`：

```tsx
import React from 'react';
import ReactDOM from 'react-dom/client';

import App from './App';
import './index.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
```

创建 `frontend/src/App.tsx`：

```tsx
import { useEffect, useState } from 'react';

type HealthState = 'checking' | 'up' | 'down';

const healthEndpoint = 'http://localhost:8080/actuator/health';

export default function App() {
  const [healthState, setHealthState] = useState<HealthState>('checking');

  useEffect(() => {
    let active = true;

    fetch(healthEndpoint)
      .then((response) => response.json())
      .then((body: { status?: string }) => {
        if (active) {
          setHealthState(body.status === 'UP' ? 'up' : 'down');
        }
      })
      .catch(() => {
        if (active) {
          setHealthState('down');
        }
      });

    return () => {
      active = false;
    };
  }, []);

  const label = healthState === 'up' ? '后端可用' : healthState === 'down' ? '后端不可用' : '检查中';

  return (
    <main className="min-h-screen bg-slate-950 px-6 py-10 text-slate-100">
      <section className="mx-auto flex max-w-4xl flex-col gap-8 rounded-3xl border border-slate-800 bg-slate-900/80 p-8 shadow-2xl shadow-black/30">
        <div className="space-y-3">
          <p className="text-sm font-semibold uppercase tracking-[0.3em] text-cyan-300">CMP Skeleton</p>
          <h1 className="text-4xl font-bold tracking-tight">CMP 工程骨架已启动</h1>
          <p className="max-w-2xl text-base leading-7 text-slate-300">
            当前页面只验证前端工程、样式入口、构建入口和后端健康检查联通，不承载业务菜单、权限或流程功能。
          </p>
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          <div className="rounded-2xl border border-slate-800 bg-slate-950 p-5">
            <h2 className="text-lg font-semibold">后端健康检查</h2>
            <p className="mt-2 font-mono text-sm text-cyan-200">/actuator/health</p>
            <p className="mt-4 text-sm text-slate-300" aria-live="polite">{label}</p>
          </div>
          <div className="rounded-2xl border border-slate-800 bg-slate-950 p-5">
            <h2 className="text-lg font-semibold">工程边界</h2>
            <p className="mt-2 text-sm leading-6 text-slate-300">
              本骨架只提供运行、测试、构建和本地编排入口。
            </p>
          </div>
        </div>
      </section>
    </main>
  );
}
```

创建 `frontend/src/index.css`：

```css
@import "tailwindcss";

:root {
  color-scheme: dark;
  font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
}

body {
  margin: 0;
}
```

创建 `frontend/src/test-setup.ts`：

```ts
import '@testing-library/jest-dom/vitest';
```

创建 `frontend/src/vite-env.d.ts`：

```ts
/// <reference types="vite/client" />
```

- [ ] **Step 5: 创建前端 Dockerfile**

创建 `frontend/Dockerfile`：

```dockerfile
FROM node:22-alpine
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
EXPOSE 5173
CMD ["pnpm", "dev", "--", "--host", "0.0.0.0"]
```

- [ ] **Step 6: 安装依赖并生成锁文件**

Run: `cd frontend && pnpm install`

Expected: 命令成功，生成 `frontend/pnpm-lock.yaml`，依赖安装完成。

- [ ] **Step 7: 运行前端测试并确认通过**

Run: `cd frontend && pnpm test -- --run`

Expected: 命令成功，输出包含 `1 passed` 或等价的 Vitest 通过统计。

- [ ] **Step 8: 运行 lint 与构建验证**

Run: `cd frontend && pnpm lint && pnpm build`

Expected: 命令成功，`eslint` 无错误，`vite build` 生成 `frontend/dist`。

- [ ] **Step 9: 提交建议**

```bash
git add frontend
git commit -m "feat: add minimal React frontend skeleton"
```

### Task 3: Docker Compose 本地编排和容器健康验证

**Files:**
- Create: `docker-compose.yml`
- Modify: `backend/Dockerfile`
- Modify: `frontend/Dockerfile`
- Test: `docker-compose.yml`

- [ ] **Step 1: 创建本地编排验证命令并确认失败**

Run: `docker compose config --quiet`

Expected: 命令失败，错误包含缺少 `docker-compose.yml` 或无法读取 Compose 配置。

- [ ] **Step 2: 创建 Docker Compose 配置**

创建 `docker-compose.yml`：

```yaml
services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_DATABASE: cmp
      MYSQL_USER: cmp
      MYSQL_PASSWORD: cmp_dev_password
      MYSQL_ROOT_PASSWORD: cmp_root_password
    ports:
      - "3306:3306"
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping -h 127.0.0.1 -uroot -p$${MYSQL_ROOT_PASSWORD}"]
      interval: 10s
      timeout: 5s
      retries: 10
    volumes:
      - cmp-mysql-data:/var/lib/mysql

  redis:
    image: redis:7.4-alpine
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 10

  backend:
    build:
      context: ./backend
    environment:
      SPRING_PROFILES_ACTIVE: local
    ports:
      - "8080:8080"
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health | grep '\"status\":\"UP\"'"]
      interval: 10s
      timeout: 5s
      retries: 12

  frontend:
    build:
      context: ./frontend
    ports:
      - "5173:5173"
    depends_on:
      backend:
        condition: service_healthy

volumes:
  cmp-mysql-data:
```

- [ ] **Step 3: 修正后端 Dockerfile 的 Maven Wrapper 依赖风险**

将 `backend/Dockerfile` 替换为以下内容，避免依赖尚未创建的 `mvnw` 文件：

```dockerfile
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/cmp-backend-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 4: 运行 Compose 配置验证并确认通过**

Run: `docker compose config --quiet`

Expected: 命令成功，退出码为 `0`。

- [ ] **Step 5: 构建本地 Compose 服务**

Run: `docker compose build`

Expected: 命令成功，`backend` 与 `frontend` 镜像构建完成。

- [ ] **Step 6: 启动本地栈并验证健康端点**

Run: `docker compose up -d && curl -fsS http://localhost:8080/actuator/health`

Expected: 命令成功，健康检查响应包含 `"status":"UP"`。

- [ ] **Step 7: 验证前端开发服务可访问**

Run: `curl -fsS http://localhost:5173`

Expected: 命令成功，响应包含 `<div id="root"></div>`。

- [ ] **Step 8: 停止本地栈**

Run: `docker compose down`

Expected: 命令成功，`backend`、`frontend`、`mysql`、`redis` 容器停止。

- [ ] **Step 9: 提交建议**

```bash
git add docker-compose.yml backend/Dockerfile frontend/Dockerfile
git commit -m "feat: add local Docker Compose stack"
```

### Task 4: 仓库级验证脚本

**Files:**
- Create: `scripts/verify-backend.sh`
- Create: `scripts/verify-frontend.sh`
- Create: `scripts/verify-local-stack.sh`
- Create: `scripts/verify-all.sh`
- Test: `scripts/verify-backend.sh`
- Test: `scripts/verify-frontend.sh`
- Test: `scripts/verify-local-stack.sh`
- Test: `scripts/verify-all.sh`

- [ ] **Step 1: 运行脚本入口并确认失败**

Run: `scripts/verify-all.sh`

Expected: 命令失败，错误包含 `No such file or directory` 或权限不可执行。

- [ ] **Step 2: 创建后端验证脚本**

创建 `scripts/verify-backend.sh`：

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "${ROOT_DIR}/backend"
mvn test
mvn -q -DskipTests package
```

- [ ] **Step 3: 创建前端验证脚本**

创建 `scripts/verify-frontend.sh`：

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "${ROOT_DIR}/frontend"
pnpm install --frozen-lockfile
pnpm lint
pnpm test -- --run
pnpm build
```

- [ ] **Step 4: 创建本地栈验证脚本**

创建 `scripts/verify-local-stack.sh`：

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "${ROOT_DIR}"

docker compose config --quiet
docker compose build
docker compose up -d

cleanup() {
  docker compose down
}
trap cleanup EXIT

for attempt in {1..30}; do
  if curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"'; then
    curl -fsS http://localhost:5173 | grep -q '<div id="root"></div>'
    exit 0
  fi
  sleep 2
done

docker compose ps
docker compose logs backend
exit 1
```

- [ ] **Step 5: 创建总验证脚本**

创建 `scripts/verify-all.sh`：

```bash
#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

"${ROOT_DIR}/scripts/verify-backend.sh"
"${ROOT_DIR}/scripts/verify-frontend.sh"
"${ROOT_DIR}/scripts/verify-local-stack.sh"
```

- [ ] **Step 6: 设置脚本可执行权限**

Run: `chmod +x scripts/verify-backend.sh scripts/verify-frontend.sh scripts/verify-local-stack.sh scripts/verify-all.sh`

Expected: 命令成功，四个脚本具备可执行权限。

- [ ] **Step 7: 运行后端验证脚本并确认通过**

Run: `scripts/verify-backend.sh`

Expected: 命令成功，后端测试通过并完成 jar 构建。

- [ ] **Step 8: 运行前端验证脚本并确认通过**

Run: `scripts/verify-frontend.sh`

Expected: 命令成功，前端依赖安装、lint、测试和构建全部通过。

- [ ] **Step 9: 运行本地栈验证脚本并确认通过**

Run: `scripts/verify-local-stack.sh`

Expected: 命令成功，`docker compose` 启动后端、前端、`mysql`、`redis`，健康端点返回 `UP`，前端 HTML 可访问，脚本退出前自动停止本地栈。

- [ ] **Step 10: 运行总验证脚本并确认通过**

Run: `scripts/verify-all.sh`

Expected: 命令成功，后端、前端、本地栈验证依次通过，任一失败会以非零状态退出。

- [ ] **Step 11: 提交建议**

```bash
git add scripts/verify-backend.sh scripts/verify-frontend.sh scripts/verify-local-stack.sh scripts/verify-all.sh
git commit -m "feat: add repository verification scripts"
```

### Task 5: 范围边界与最终质量验收

**Files:**
- Read: `docs/superpowers/specs/101-minimal-runnable-skeleton-design.md`
- Read: `docs/superpowers/specs/102-cmp-implementation-execution-spec.md`
- Read: `docs/superpowers/plans/102-01-batch-1-foundations-implementation-plan.md`
- Verify: `backend/**`
- Verify: `frontend/**`
- Verify: `docker-compose.yml`
- Verify: `scripts/verify-backend.sh`
- Verify: `scripts/verify-frontend.sh`
- Verify: `scripts/verify-local-stack.sh`
- Verify: `scripts/verify-all.sh`

- [ ] **Step 1: 扫描禁止提前实现的业务对象**

Run: `rg -n "\b(User|Role|Permission|Organization|AgentTask|AgentRun|QueryEngine|IntegrationJob|Contract|Workflow|Audit)\b|身份|组织|权限|合同|流程|入站|出站|回调|补偿|对账" backend frontend docker-compose.yml scripts`

Expected: 命令不应命中业务模型、业务服务、业务 API、业务数据迁移或业务页面。允许命中 `App.tsx` 中说明骨架不承载业务功能的中文边界文案。

- [ ] **Step 2: 确认没有业务数据库迁移**

Run: `test ! -d backend/src/main/resources/db/migration`

Expected: 命令成功，骨架阶段没有新增正式业务迁移目录。

- [ ] **Step 3: 确认后端只暴露健康端点**

Run: `rg -n "@(RestController|Controller|RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping)" backend/src/main/java`

Expected: 命令不命中任何业务 Controller；健康端点由 `spring-boot-starter-actuator` 提供。

- [ ] **Step 4: 确认前端没有业务路由和业务菜单**

Run: `rg -n "createBrowserRouter|Routes|Route|menu|permission|role|organization|agent|integration|contract|workflow" frontend/src`

Expected: 命令不命中业务路由、业务菜单、权限逻辑或业务页面。

- [ ] **Step 5: 运行最终总验证**

Run: `scripts/verify-all.sh`

Expected: 命令成功，后端测试与构建、前端 lint/test/build、本地 Compose 健康检查全部通过。

- [ ] **Step 6: 记录质量验收证据**

在实现 SubAgent 的交付回执中记录以下内容，不新增计划外文件：

```markdown
已完成文件：backend、frontend、docker-compose.yml、scripts/verify-*.sh
验证命令：scripts/verify-all.sh
验证结果：PASS
范围边界：未新增身份、组织、权限、Agent、集成、合同、文档、流程等业务对象
剩余风险：无阻断项
```

- [ ] **Step 7: 提交建议**

```bash
git add backend frontend docker-compose.yml scripts
git commit -m "feat: complete minimal runnable skeleton"
```

## 4. 验证矩阵

| 验收点 | 验证任务 | 命令 | 期望结果 |
| --- | --- | --- | --- |
| 后端应用上下文可加载 | Task 1 | `cd backend && mvn test` | `CmpApplicationTests` 通过 |
| 后端健康端点可访问 | Task 1、Task 4 | `curl -fsS http://localhost:8080/actuator/health` | 响应包含 `"status":"UP"` |
| 前端页面可渲染 | Task 2 | `cd frontend && pnpm test -- --run` | `App.test.tsx` 通过 |
| 前端 lint 可执行 | Task 2、Task 4 | `cd frontend && pnpm lint` | 无 lint 错误 |
| 前端可构建 | Task 2、Task 4 | `cd frontend && pnpm build` | 生成 `frontend/dist` |
| Compose 配置合法 | Task 3、Task 4 | `docker compose config --quiet` | 退出码为 `0` |
| 本地栈可启动 | Task 3、Task 4 | `scripts/verify-local-stack.sh` | 后端、前端、`mysql`、`redis` 均可运行 |
| 总质量入口可复用 | Task 4、Task 5 | `scripts/verify-all.sh` | 所有验证通过，失败即退出 |
| 未提前实现业务对象 | Task 5 | `rg` 范围扫描命令 | 不出现业务模型、业务服务、业务路由或业务迁移 |

## 5. 独立质量审查要求

- 独立质量审查 SubAgent 必须复核 `docs/superpowers/specs/101-minimal-runnable-skeleton-design.md` 的目标、非目标、测试策略和完成判定。
- 审查必须运行或复核 `scripts/verify-all.sh` 的完整输出，不接受只展示文件列表作为完成证据。
- 审查必须确认 `backend` 没有业务 Controller、业务领域对象、业务迁移脚本。
- 审查必须确认 `frontend` 没有业务路由、业务菜单、业务权限判断。
- 审查必须确认 `docker-compose.yml` 只表达本地开发和验证依赖，不表达生产容量规划。
- 审查必须确认验证脚本使用 `set -euo pipefail`，且任一失败会阻断总验证入口。

## 6. 完成判定

- `backend` 可以通过 `mvn test` 和 `mvn -q -DskipTests package`。
- `/actuator/health` 返回 `UP`。
- `frontend` 可以通过 `pnpm lint`、`pnpm test -- --run` 和 `pnpm build`。
- `docker compose up -d` 能启动 `backend`、`frontend`、`mysql`、`redis`。
- `scripts/verify-backend.sh`、`scripts/verify-frontend.sh`、`scripts/verify-local-stack.sh`、`scripts/verify-all.sh` 均可执行。
- 未新增身份、组织、权限、Agent、集成、合同、文档、流程等业务对象。
- 后续第一批底座能力可基于该骨架继续编码，不需要重建工程结构。
