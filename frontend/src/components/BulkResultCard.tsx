import React from 'react';
import type { BulkCreateResponse } from '../types';
import { CheckCircle, XCircle, Copy, ExternalLink } from 'lucide-react';
import { motion } from 'framer-motion';
import toast from 'react-hot-toast';

interface BulkResultCardProps {
    data: BulkCreateResponse;
}

export const BulkResultCard: React.FC<BulkResultCardProps> = ({ data }) => {
    const copyToClipboard = (text: string) => {
        navigator.clipboard.writeText(text);
        toast.success('Copied to clipboard!');
    };

    return (
        <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            className="w-full max-w-4xl mx-auto bg-white shadow-xl rounded-2xl border border-gray-100 overflow-hidden"
        >
            <div className="p-8">
                <div className="flex items-center justify-between mb-6">
                    <h2 className="text-2xl font-bold text-gray-900">Bulk URL Creation Results</h2>
                    <div className="flex items-center space-x-4">
                        <div className="flex items-center text-green-600">
                            <CheckCircle className="w-5 h-5 mr-1" />
                            <span className="font-semibold">{data.successCount} Success</span>
                        </div>
                        {data.failedCount > 0 && (
                            <div className="flex items-center text-red-600">
                                <XCircle className="w-5 h-5 mr-1" />
                                <span className="font-semibold">{data.failedCount} Failed</span>
                            </div>
                        )}
                    </div>
                </div>

                {/* Success Results */}
                {data.results.length > 0 && (
                    <div className="mb-6">
                        <h3 className="text-lg font-semibold text-gray-700 mb-4">Successfully Created URLs</h3>
                        <div className="space-y-3 max-h-96 overflow-y-auto">
                            {data.results.map((result, index) => (
                                <motion.div
                                    key={index}
                                    initial={{ opacity: 0, x: -20 }}
                                    animate={{ opacity: 1, x: 0 }}
                                    transition={{ delay: index * 0.05 }}
                                    className="bg-green-50 border border-green-200 rounded-lg p-4"
                                >
                                    <div className="flex items-start justify-between">
                                        <div className="flex-1 min-w-0">
                                            <div className="flex items-center space-x-2 mb-2">
                                                <CheckCircle className="w-4 h-4 text-green-600 flex-shrink-0" />
                                                <a
                                                    href={result.shortUrl}
                                                    target="_blank"
                                                    rel="noopener noreferrer"
                                                    className="text-indigo-600 hover:text-indigo-700 font-medium truncate"
                                                >
                                                    {result.shortUrl}
                                                </a>
                                                <ExternalLink className="w-3 h-3 text-gray-400" />
                                            </div>
                                            {result.metadata?.title && (
                                                <p className="text-sm text-gray-600 truncate ml-6">
                                                    {result.metadata.title}
                                                </p>
                                            )}
                                            {result.customAlias && (
                                                <p className="text-xs text-gray-500 ml-6">
                                                    Alias: {result.customAlias}
                                                </p>
                                            )}
                                        </div>
                                        <button
                                            onClick={() => copyToClipboard(result.shortUrl)}
                                            className="ml-4 p-2 text-gray-500 hover:text-indigo-600 hover:bg-indigo-50 rounded-lg transition-colors"
                                            title="Copy URL"
                                        >
                                            <Copy className="w-4 h-4" />
                                        </button>
                                    </div>
                                </motion.div>
                            ))}
                        </div>
                    </div>
                )}

                {/* Errors */}
                {data.errors.length > 0 && (
                    <div>
                        <h3 className="text-lg font-semibold text-gray-700 mb-4">Failed URLs</h3>
                        <div className="space-y-3 max-h-64 overflow-y-auto">
                            {data.errors.map((error, index) => (
                                <motion.div
                                    key={index}
                                    initial={{ opacity: 0, x: -20 }}
                                    animate={{ opacity: 1, x: 0 }}
                                    transition={{ delay: (data.results.length + index) * 0.05 }}
                                    className="bg-red-50 border border-red-200 rounded-lg p-4"
                                >
                                    <div className="flex items-start space-x-2">
                                        <XCircle className="w-4 h-4 text-red-600 flex-shrink-0 mt-0.5" />
                                        <div className="flex-1 min-w-0">
                                            <p className="text-sm font-medium text-red-800 truncate">
                                                {error.originalUrl}
                                            </p>
                                            <p className="text-xs text-red-600 mt-1">{error.error}</p>
                                        </div>
                                    </div>
                                </motion.div>
                            ))}
                        </div>
                    </div>
                )}

                {/* Download Results as CSV */}
                {data.results.length > 0 && (
                    <div className="mt-6 pt-6 border-t border-gray-200">
                        <button
                            onClick={() => {
                                const csvContent = [
                                    ['Short URL', 'Short Key', 'Custom Alias', 'Title'].join(','),
                                    ...data.results.map(r => [
                                        r.shortUrl,
                                        r.shortKey,
                                        r.customAlias || '',
                                        r.metadata?.title || ''
                                    ].map(field => `"${field}"`).join(','))
                                ].join('\n');
                                
                                const blob = new Blob([csvContent], { type: 'text/csv' });
                                const url = window.URL.createObjectURL(blob);
                                const a = document.createElement('a');
                                a.href = url;
                                a.download = `shortened-urls-${new Date().toISOString().split('T')[0]}.csv`;
                                a.click();
                                window.URL.revokeObjectURL(url);
                                toast.success('Results downloaded as CSV!');
                            }}
                            className="w-full bg-gray-100 hover:bg-gray-200 text-gray-700 font-medium py-3 px-6 rounded-lg transition-colors"
                        >
                            Download Results as CSV
                        </button>
                    </div>
                )}
            </div>
        </motion.div>
    );
};

