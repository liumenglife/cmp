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
            <p className="mt-4 text-sm text-slate-300" aria-live="polite">
              {label}
            </p>
          </div>
          <div className="rounded-2xl border border-slate-800 bg-slate-950 p-5">
            <h2 className="text-lg font-semibold">工程边界</h2>
            <p className="mt-2 text-sm leading-6 text-slate-300">本骨架只提供运行、测试、构建和本地编排入口。</p>
          </div>
        </div>
      </section>
    </main>
  );
}
