import ReactECharts from 'echarts-for-react';
import { BarChart3 } from 'lucide-react';

interface DataVizPanelProps {
  chartOption: Record<string, unknown> | null;
}

export function DataVizPanel({ chartOption }: DataVizPanelProps) {
  if (!chartOption) {
    return (
      <div className="bg-panel border border-border rounded-lg p-4 h-full flex flex-col">
        <div className="flex items-center gap-2 mb-3">
          <BarChart3 className="w-4 h-4 text-text-dim" />
          <span className="text-xs font-semibold text-text uppercase tracking-wider">
            Data Visualization
          </span>
        </div>
        <div className="flex-1 flex items-center justify-center">
          <p className="text-text-dim text-xs text-center">
            No data to display.
            <br />
            Execute a data query to see visualizations.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-panel border border-border rounded-lg p-4 h-full flex flex-col">
      <div className="flex items-center gap-2 mb-2">
        <BarChart3 className="w-4 h-4 text-accent" />
        <span className="text-xs font-semibold text-text uppercase tracking-wider">
          Real-time Visualization
        </span>
      </div>
      <div className="flex-1 min-h-0">
        <ReactECharts
          option={chartOption}
          style={{ height: '100%', width: '100%' }}
          notMerge
        />
      </div>
    </div>
  );
}
