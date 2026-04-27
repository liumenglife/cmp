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
