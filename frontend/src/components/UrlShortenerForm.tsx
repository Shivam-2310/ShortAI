import React, { useState } from 'react';
import type { CreateUrlRequest, CreateUrlResponse } from '../types';
import { urlService } from '../services/api';
import { Loader2, Settings, Lock, Calendar, Tag, Brain, QrCode as QrIcon } from 'lucide-react';
import toast from 'react-hot-toast';
import { motion, AnimatePresence } from 'framer-motion';

interface UrlShortenerFormProps {
    onSuccess: (response: CreateUrlResponse) => void;
}

export const UrlShortenerForm: React.FC<UrlShortenerFormProps> = ({ onSuccess }) => {
    const [originalUrl, setOriginalUrl] = useState('');
    const [loading, setLoading] = useState(false);
    const [showOptions, setShowOptions] = useState(false);

    const [options, setOptions] = useState<Omit<CreateUrlRequest, 'originalUrl'>>({
        customAlias: '',
        password: '',
        expiresAt: '',
        fetchMetadata: true,
        enableAiAnalysis: true,
        generateQrCode: true
    });

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!originalUrl) return;

        setLoading(true);
        try {
            // Clean up empty options
            const requestData: CreateUrlRequest = {
                originalUrl,
                fetchMetadata: options.fetchMetadata,
                enableAiAnalysis: options.enableAiAnalysis,
                generateQrCode: options.generateQrCode
            };

            if (options.customAlias) requestData.customAlias = options.customAlias;
            if (options.password) requestData.password = options.password;
            if (options.expiresAt) {
                // Format to yyyy-MM-dd'T'HH:mm:ss (strip milliseconds and Z)
                const date = new Date(options.expiresAt);
                const formatted = date.toISOString().slice(0, 19);
                requestData.expiresAt = formatted;
            }

            const response = await urlService.createShortUrl(requestData);
            onSuccess(response);
            toast.success('URL shortened successfully!');
            setOriginalUrl('');
            setOptions({ ...options, customAlias: '', password: '', expiresAt: '' });
        } catch (error: any) {
            console.error(error);
            const msg = error.response?.data?.message || 'Failed to shorten URL';
            toast.error(msg);
        } finally {
            setLoading(false);
        }
    };

    const getMinDateTime = () => {
        const now = new Date();
        now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
        return now.toISOString().slice(0, 16);
    };

    return (
        <div className="w-full max-w-2xl mx-auto">
            <form onSubmit={handleSubmit} className="bg-white shadow-xl rounded-2xl overflow-hidden border border-gray-100">
                <div className="p-8">
                    <div className="flex flex-col space-y-4">
                        <label htmlFor="url-input" className="text-lg font-semibold text-gray-700">
                            Paste your long URL
                        </label>
                        <div className="relative">
                            <input
                                id="url-input"
                                type="url"
                                required
                                placeholder="https://example.com/very/long/url"
                                className="w-full px-5 py-4 text-lg border-2 border-gray-200 rounded-xl focus:border-indigo-500 focus:ring-0 transition-colors bg-gray-50 focus:bg-white"
                                value={originalUrl}
                                onChange={(e) => setOriginalUrl(e.target.value)}
                            />
                        </div>
                    </div>

                    <div className="mt-6 flex items-center justify-between">
                        <button
                            type="button"
                            onClick={() => setShowOptions(!showOptions)}
                            className="flex items-center text-sm font-medium text-gray-600 hover:text-indigo-600 transition-colors"
                        >
                            <Settings className="w-4 h-4 mr-2" />
                            {showOptions ? 'Hide Options' : 'Advanced Options'}
                        </button>

                        <div className="flex space-x-4">
                            <label className="flex items-center space-x-2 text-sm text-gray-600 cursor-pointer">
                                <input
                                    type="checkbox"
                                    checked={options.enableAiAnalysis}
                                    onChange={(e) => setOptions({ ...options, enableAiAnalysis: e.target.checked })}
                                    className="rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
                                />
                                <span className="flex items-center"><Brain className="w-3 h-3 mr-1" /> AI Analysis</span>
                            </label>
                            
                            <label className="flex items-center space-x-2 text-sm text-gray-600 cursor-pointer">
                                <input
                                    type="checkbox"
                                    checked={options.generateQrCode}
                                    onChange={(e) => setOptions({ ...options, generateQrCode: e.target.checked })}
                                    className="rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
                                />
                                <span className="flex items-center"><QrIcon className="w-3 h-3 mr-1" /> QR Code</span>
                            </label>
                        </div>
                    </div>

                    <AnimatePresence>
                        {showOptions && (
                            <motion.div
                                initial={{ height: 0, opacity: 0 }}
                                animate={{ height: 'auto', opacity: 1 }}
                                exit={{ height: 0, opacity: 0 }}
                                className="overflow-hidden"
                            >
                                <div className="mt-6 grid grid-cols-1 md:grid-cols-2 gap-6 pt-6 border-t border-gray-100">
                                    {/* Custom Alias */}
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 mb-2 flex items-center">
                                            <Tag className="w-4 h-4 mr-2 text-gray-400" /> Custom Alias
                                        </label>
                                        <input
                                            type="text"
                                            placeholder="my-link"
                                            className="w-full px-4 py-2 border border-gray-200 rounded-lg focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500"
                                            value={options.customAlias}
                                            onChange={(e) => setOptions({ ...options, customAlias: e.target.value })}
                                        />
                                        <p className="mt-1 text-xs text-gray-400">Optional (3-50 chars)</p>
                                    </div>

                                    {/* Password */}
                                    <div>
                                        <label className="block text-sm font-medium text-gray-700 mb-2 flex items-center">
                                            <Lock className="w-4 h-4 mr-2 text-gray-400" /> Password Protection
                                        </label>
                                        <input
                                            type="password"
                                            placeholder="Secret123"
                                            className="w-full px-4 py-2 border border-gray-200 rounded-lg focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500"
                                            value={options.password}
                                            onChange={(e) => setOptions({ ...options, password: e.target.value })}
                                        />
                                    </div>

                                    {/* Expiration */}
                                    <div className="md:col-span-2">
                                        <label className="block text-sm font-medium text-gray-700 mb-2 flex items-center">
                                            <Calendar className="w-4 h-4 mr-2 text-gray-400" /> Expiration Date
                                        </label>
                                        <input
                                            type="datetime-local"
                                            className="w-full px-4 py-2 border border-gray-200 rounded-lg focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500"
                                            value={options.expiresAt}
                                            onChange={(e) => setOptions({ ...options, expiresAt: e.target.value })}
                                            min={getMinDateTime()}
                                        />
                                    </div>
                                </div>
                            </motion.div>
                        )}
                    </AnimatePresence>

                    <div className="mt-8">
                        <button
                            type="submit"
                            disabled={loading || !originalUrl}
                            className="w-full bg-indigo-600 hover:bg-indigo-700 text-white font-bold py-4 px-6 rounded-xl shadow-lg hover:shadow-xl transition-all disabled:opacity-50 disabled:cursor-not-allowed flex justify-center items-center text-lg"
                        >
                            {loading ? (
                                <>
                                    <Loader2 className="animate-spin mr-2 h-6 w-6" />
                                    Shortening & Analyzing...
                                </>
                            ) : (
                                'Shorten URL'
                            )}
                        </button>
                    </div>
                </div>
            </form>
        </div>
    );
};

