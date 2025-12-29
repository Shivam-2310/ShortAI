import React, { useState } from 'react';
import { UrlShortenerForm } from '../components/UrlShortenerForm';
import { CsvUploadForm } from '../components/CsvUploadForm';
import { ResultCard } from '../components/ResultCard';
import { BulkResultCard } from '../components/BulkResultCard';
import type { CreateUrlResponse, BulkCreateResponse } from '../types';
import { motion } from 'framer-motion';
import { Link, FileText } from 'lucide-react';

type TabType = 'single' | 'bulk';

export const HomePage: React.FC = () => {
    const [activeTab, setActiveTab] = useState<TabType>('single');
    const [singleResult, setSingleResult] = useState<CreateUrlResponse | null>(null);
    const [bulkResult, setBulkResult] = useState<BulkCreateResponse | null>(null);

    return (
        <div className="space-y-12 pb-12">
            <div className="text-center space-y-4 pt-10">
                <motion.h1 
                    initial={{ opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    className="text-4xl md:text-6xl font-extrabold text-gray-900 tracking-tight"
                >
                    Shorten Your Links <br />
                    <span className="text-indigo-600">Analyze with AI</span>
                </motion.h1>
                <motion.p 
                    initial={{ opacity: 0, y: 20 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: 0.1 }}
                    className="text-xl text-gray-500 max-w-2xl mx-auto"
                >
                    The most advanced URL shortener with built-in AI analysis, detailed analytics, and QR code generation.
                </motion.p>
            </div>

            {/* Tabs */}
            <div className="flex justify-center">
                <div className="inline-flex bg-gray-100 rounded-xl p-1">
                    <button
                        onClick={() => {
                            setActiveTab('single');
                            setSingleResult(null);
                            setBulkResult(null);
                        }}
                        className={`flex items-center space-x-2 px-6 py-3 rounded-lg font-medium transition-all ${
                            activeTab === 'single'
                                ? 'bg-white text-indigo-600 shadow-sm'
                                : 'text-gray-600 hover:text-gray-900'
                        }`}
                    >
                        <Link className="w-4 h-4" />
                        <span>Single URL</span>
                    </button>
                    <button
                        onClick={() => {
                            setActiveTab('bulk');
                            setSingleResult(null);
                            setBulkResult(null);
                        }}
                        className={`flex items-center space-x-2 px-6 py-3 rounded-lg font-medium transition-all ${
                            activeTab === 'bulk'
                                ? 'bg-white text-indigo-600 shadow-sm'
                                : 'text-gray-600 hover:text-gray-900'
                        }`}
                    >
                        <FileText className="w-4 h-4" />
                        <span>Bulk CSV Upload</span>
                    </button>
                </div>
            </div>

            {/* Form Content */}
            {activeTab === 'single' ? (
                <>
                    <UrlShortenerForm onSuccess={setSingleResult} />
                    {singleResult && <ResultCard data={singleResult} />}
                </>
            ) : (
                <>
                    <CsvUploadForm onSuccess={setBulkResult} />
                    {bulkResult && <BulkResultCard data={bulkResult} />}
                </>
            )}

            {/* Features Grid */}
            {!singleResult && !bulkResult && (
                <motion.div 
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    transition={{ delay: 0.3 }}
                    className="grid grid-cols-1 md:grid-cols-3 gap-8 mt-20"
                >
                    <Feature 
                        title="AI-Powered"
                        desc="Automatic content summarization and safety scoring using local LLMs."
                        icon="🤖"
                    />
                    <Feature 
                        title="Deep Analytics"
                        desc="Track clicks by country, device, and referrer in real-time."
                        icon="📊"
                    />
                    <Feature 
                        title="Secure & Fast"
                        desc="Enterprise-grade security with rate limiting and password protection."
                        icon="🔒"
                    />
                </motion.div>
            )}
        </div>
    );
};

const Feature = ({ title, desc, icon }: { title: string, desc: string, icon: string }) => (
    <div className="bg-white p-6 rounded-xl border border-gray-100 shadow-sm hover:shadow-md transition-shadow">
        <div className="text-4xl mb-4">{icon}</div>
        <h3 className="text-lg font-bold text-gray-900 mb-2">{title}</h3>
        <p className="text-gray-500">{desc}</p>
    </div>
);

