import React from 'react';
import type { DetailedAnalytics } from '../types';
import {
    BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
    PieChart, Pie, Cell, LineChart, Line
} from 'recharts';
import { Smartphone, MousePointer, Monitor } from 'lucide-react';

interface AnalyticsDashboardProps {
    data: DetailedAnalytics;
}

const COLORS = ['#4F46E5', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6'];

export const AnalyticsDashboard: React.FC<AnalyticsDashboardProps> = ({ data }) => {
    
    // Transform map objects to arrays for Recharts
    const toChartData = (map: Record<string, number>) => 
        Object.entries(map || {}).map(([name, value]) => ({ name, value })).sort((a, b) => b.value - a.value).slice(0, 5);

    const deviceData = toChartData(data.clicksByDevice);
    const browserData = toChartData(data.clicksByBrowser);
    const osData = toChartData(data.clicksByOs);
    const timeData = Object.entries(data.clicksOverTime || {}).map(([date, count]) => ({
        date: new Date(date).toLocaleDateString(),
        clicks: count
    }));

    return (
        <div className="space-y-6">
            {/* Header Stats */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <StatCard icon={<MousePointer className="text-indigo-600" />} label="Total Clicks" value={data.totalClicks || 0} />
                <StatCard icon={<Smartphone className="text-blue-600" />} label="Top Device" value={deviceData[0]?.name || 'N/A'} />
                <StatCard icon={<Monitor className="text-purple-600" />} label="Top OS" value={osData[0]?.name || 'N/A'} />
            </div>

            {/* Main Charts */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {/* Clicks Over Time */}
                <ChartCard title="Clicks Over Time">
                    {timeData.length > 0 ? (
                        <div className="w-full h-[300px]">
                            <ResponsiveContainer width="100%" height="100%">
                                <LineChart data={timeData} margin={{ top: 5, right: 10, left: 0, bottom: 5 }}>
                                    <CartesianGrid strokeDasharray="3 3" stroke="#eee" />
                                    <XAxis dataKey="date" stroke="#888" fontSize={12} />
                                    <YAxis stroke="#888" fontSize={12} />
                                    <Tooltip contentStyle={{ borderRadius: '8px', border: 'none', boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)' }} />
                                    <Line type="monotone" dataKey="clicks" stroke="#4F46E5" strokeWidth={3} dot={{ fill: '#4F46E5' }} />
                                </LineChart>
                            </ResponsiveContainer>
                        </div>
                    ) : (
                        <div className="flex items-center justify-center h-[300px] text-gray-400">
                            <p>No time-series data available</p>
                        </div>
                    )}
                </ChartCard>

                {/* Device Distribution */}
                <ChartCard title="Device Types">
                    {deviceData.length > 0 ? (
                        <>
                            <div className="w-full h-[300px]">
                                <ResponsiveContainer width="100%" height="100%">
                                    <PieChart>
                                        <Pie
                                            data={deviceData}
                                            cx="50%"
                                            cy="50%"
                                            innerRadius={60}
                                            outerRadius={80}
                                            fill="#8884d8"
                                            paddingAngle={5}
                                            dataKey="value"
                                        >
                                            {deviceData.map((_, index) => (
                                                <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                                            ))}
                                        </Pie>
                                        <Tooltip />
                                    </PieChart>
                                </ResponsiveContainer>
                            </div>
                            <div className="flex justify-center gap-4 text-sm text-gray-600 flex-wrap mt-4">
                                {deviceData.map((entry, index) => (
                                    <div key={index} className="flex items-center">
                                        <span className="w-3 h-3 rounded-full mr-1" style={{ backgroundColor: COLORS[index % COLORS.length] }}></span>
                                        {entry.name} ({entry.value})
                                    </div>
                                ))}
                            </div>
                        </>
                    ) : (
                        <div className="flex items-center justify-center h-[300px] text-gray-400">
                            <p>No device data available</p>
                        </div>
                    )}
                </ChartCard>
                
                {/* Browser Distribution */}
                 <ChartCard title="Top Browsers">
                    {browserData.length > 0 ? (
                        <div className="w-full h-[300px]">
                            <ResponsiveContainer width="100%" height="100%">
                                <BarChart data={browserData} margin={{ top: 5, right: 10, left: 0, bottom: 5 }}>
                                    <CartesianGrid strokeDasharray="3 3" vertical={false} />
                                    <XAxis dataKey="name" fontSize={12} />
                                    <YAxis hide />
                                    <Tooltip cursor={{ fill: '#f3f4f6' }} />
                                    <Bar dataKey="value" fill="#F59E0B" radius={[4, 4, 0, 0]} barSize={40} />
                                </BarChart>
                            </ResponsiveContainer>
                        </div>
                    ) : (
                        <div className="flex items-center justify-center h-[300px] text-gray-400">
                            <p>No browser data available</p>
                        </div>
                    )}
                </ChartCard>

                {/* OS Distribution */}
                <ChartCard title="Operating Systems">
                    {osData.length > 0 ? (
                        <div className="w-full h-[300px]">
                            <ResponsiveContainer width="100%" height="100%">
                                <BarChart data={osData} margin={{ top: 5, right: 10, left: 0, bottom: 5 }}>
                                    <CartesianGrid strokeDasharray="3 3" vertical={false} />
                                    <XAxis dataKey="name" fontSize={12} />
                                    <YAxis hide />
                                    <Tooltip cursor={{ fill: '#f3f4f6' }} />
                                    <Bar dataKey="value" fill="#8B5CF6" radius={[4, 4, 0, 0]} barSize={40} />
                                </BarChart>
                            </ResponsiveContainer>
                        </div>
                    ) : (
                        <div className="flex items-center justify-center h-[300px] text-gray-400">
                            <p>No OS data available</p>
                        </div>
                    )}
                </ChartCard>
            </div>
            
             {/* Metadata Info */}
            <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
                <h3 className="text-lg font-semibold text-gray-900 mb-4">Link Metadata</h3>
                <div className="flex gap-4">
                    {data.metaImageUrl && (
                        <img src={data.metaImageUrl} alt="Preview" className="w-24 h-24 object-cover rounded-lg" />
                    )}
                    <div>
                        <h4 className="font-medium text-gray-900">{data.metaTitle || 'No Title'}</h4>
                        <p className="text-sm text-gray-500 mt-1">{data.metaDescription || 'No description available.'}</p>
                        <a href={data.originalUrl} target="_blank" rel="noreferrer" className="text-indigo-600 text-sm mt-2 block hover:underline truncate max-w-md">
                            {data.originalUrl}
                        </a>
                    </div>
                </div>
            </div>
        </div>
    );
};

const StatCard = ({ icon, label, value }: { icon: React.ReactNode, label: string, value: string | number }) => (
    <div className="bg-white p-6 rounded-xl shadow-sm border border-gray-100 flex items-center space-x-4">
        <div className="p-3 bg-gray-50 rounded-lg">
            {icon}
        </div>
        <div>
            <p className="text-sm text-gray-500">{label}</p>
            <p className="text-2xl font-bold text-gray-900">{value}</p>
        </div>
    </div>
);

const ChartCard = ({ title, children }: { title: string, children: React.ReactNode }) => (
    <div className="bg-white p-6 rounded-xl shadow-sm border border-gray-100 h-full flex flex-col">
        <h3 className="text-lg font-semibold text-gray-900 mb-6">{title}</h3>
        <div className="flex-1 min-h-0">
            {children}
        </div>
    </div>
);

