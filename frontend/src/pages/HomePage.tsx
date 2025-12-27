import React, { useState } from 'react';
import { UrlShortenerForm } from '../components/UrlShortenerForm';
import { ResultCard } from '../components/ResultCard';
import type { CreateUrlResponse } from '../types';
import { motion } from 'framer-motion';

export const HomePage: React.FC = () => {
    const [result, setResult] = useState<CreateUrlResponse | null>(null);

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

            <UrlShortenerForm onSuccess={setResult} />

            {result && <ResultCard data={result} />}

            {/* Features Grid */}
            {!result && (
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

