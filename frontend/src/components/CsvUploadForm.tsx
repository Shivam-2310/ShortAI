import React, { useState, useRef } from 'react';
import type { BulkCreateResponse } from '../types';
import { urlService } from '../services/api';
import { Loader2, Upload, FileText, CheckCircle, Settings, Brain } from 'lucide-react';
import toast from 'react-hot-toast';
import { motion, AnimatePresence } from 'framer-motion';

interface CsvUploadFormProps {
    onSuccess: (response: BulkCreateResponse) => void;
}

export const CsvUploadForm: React.FC<CsvUploadFormProps> = ({ onSuccess }) => {
    const [file, setFile] = useState<File | null>(null);
    const [loading, setLoading] = useState(false);
    const [showOptions, setShowOptions] = useState(false);
    const [fetchMetadata, setFetchMetadata] = useState(false);
    const [enableAiAnalysis, setEnableAiAnalysis] = useState(false);
    const fileInputRef = useRef<HTMLInputElement>(null);

    const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
        const selectedFile = e.target.files?.[0];
        if (selectedFile) {
            if (!selectedFile.name.endsWith('.csv')) {
                toast.error('Please select a CSV file');
                return;
            }
            if (selectedFile.size > 1024 * 1024) {
                toast.error('File size must be less than 1MB');
                return;
            }
            setFile(selectedFile);
        }
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!file) {
            toast.error('Please select a CSV file');
            return;
        }

        setLoading(true);
        try {
            const response = await urlService.createBulkShortUrlsFromCsv(
                file,
                fetchMetadata,
                enableAiAnalysis
            );
            onSuccess(response);
            
            if (response.successCount > 0) {
                toast.success(`Successfully shortened ${response.successCount} URL(s)!`);
            }
            if (response.failedCount > 0) {
                toast.error(`${response.failedCount} URL(s) failed to shorten`);
            }
            
            // Reset form
            setFile(null);
            if (fileInputRef.current) {
                fileInputRef.current.value = '';
            }
        } catch (error: any) {
            console.error(error);
            const msg = error.response?.data?.message || 'Failed to process CSV file';
            toast.error(msg);
        } finally {
            setLoading(false);
        }
    };

    const handleDragOver = (e: React.DragEvent) => {
        e.preventDefault();
        e.stopPropagation();
    };

    const handleDrop = (e: React.DragEvent) => {
        e.preventDefault();
        e.stopPropagation();
        
        const droppedFile = e.dataTransfer.files[0];
        if (droppedFile) {
            if (!droppedFile.name.endsWith('.csv')) {
                toast.error('Please drop a CSV file');
                return;
            }
            if (droppedFile.size > 1024 * 1024) {
                toast.error('File size must be less than 1MB');
                return;
            }
            setFile(droppedFile);
        }
    };

    return (
        <div className="w-full max-w-2xl mx-auto">
            <form onSubmit={handleSubmit} className="bg-white shadow-xl rounded-2xl overflow-hidden border border-gray-100">
                <div className="p-8">
                    <div className="space-y-6">
                        <div>
                            <label htmlFor="csv-input" className="text-lg font-semibold text-gray-700 mb-4 block">
                                Upload CSV File with URLs
                            </label>
                            
                            {/* File Drop Zone */}
                            <div
                                onDragOver={handleDragOver}
                                onDrop={handleDrop}
                                className={`border-2 border-dashed rounded-xl p-8 text-center transition-colors ${
                                    file
                                        ? 'border-green-400 bg-green-50'
                                        : 'border-gray-300 bg-gray-50 hover:border-indigo-400 hover:bg-indigo-50'
                                }`}
                            >
                                <input
                                    ref={fileInputRef}
                                    id="csv-input"
                                    type="file"
                                    accept=".csv"
                                    onChange={handleFileSelect}
                                    className="hidden"
                                />
                                
                                {file ? (
                                    <div className="space-y-2">
                                        <CheckCircle className="w-12 h-12 text-green-500 mx-auto" />
                                        <p className="text-lg font-medium text-gray-700">{file.name}</p>
                                        <p className="text-sm text-gray-500">
                                            {(file.size / 1024).toFixed(2)} KB
                                        </p>
                                        <button
                                            type="button"
                                            onClick={() => {
                                                setFile(null);
                                                if (fileInputRef.current) {
                                                    fileInputRef.current.value = '';
                                                }
                                            }}
                                            className="text-sm text-red-600 hover:text-red-700 mt-2"
                                        >
                                            Remove file
                                        </button>
                                    </div>
                                ) : (
                                    <div className="space-y-2">
                                        <Upload className="w-12 h-12 text-gray-400 mx-auto" />
                                        <p className="text-gray-600">
                                            Drag and drop your CSV file here, or
                                        </p>
                                        <button
                                            type="button"
                                            onClick={() => fileInputRef.current?.click()}
                                            className="text-indigo-600 hover:text-indigo-700 font-medium"
                                        >
                                            browse to upload
                                        </button>
                                        <p className="text-xs text-gray-400 mt-2">
                                            One URL per line. Max 100 URLs, 1MB file size.
                                        </p>
                                    </div>
                                )}
                            </div>
                        </div>

                        {/* CSV Format Info */}
                        <div className="bg-blue-50 border border-blue-200 rounded-lg p-4">
                            <div className="flex items-start">
                                <FileText className="w-5 h-5 text-blue-600 mt-0.5 mr-2 flex-shrink-0" />
                                <div className="text-sm text-blue-800">
                                    <p className="font-medium mb-1">CSV Format:</p>
                                    <ul className="list-disc list-inside space-y-1 text-blue-700">
                                        <li>One URL per line (simple format)</li>
                                        <li>Or CSV with header row: <code className="bg-blue-100 px-1 rounded">url</code> or <code className="bg-blue-100 px-1 rounded">originalUrl</code></li>
                                        <li>Example: <code className="bg-blue-100 px-1 rounded">https://example.com/page1</code></li>
                                    </ul>
                                </div>
                            </div>
                        </div>

                        <div className="flex items-center justify-between">
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
                                        checked={enableAiAnalysis}
                                        onChange={(e) => setEnableAiAnalysis(e.target.checked)}
                                        className="rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
                                    />
                                    <span className="flex items-center"><Brain className="w-3 h-3 mr-1" /> AI Analysis</span>
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
                                    <div className="pt-4 border-t border-gray-100">
                                        <label className="flex items-center space-x-2 text-sm text-gray-600 cursor-pointer">
                                            <input
                                                type="checkbox"
                                                checked={fetchMetadata}
                                                onChange={(e) => setFetchMetadata(e.target.checked)}
                                                className="rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
                                            />
                                            <span>Fetch metadata for all URLs</span>
                                        </label>
                                        <p className="text-xs text-gray-400 mt-1 ml-6">
                                            Note: Enabling metadata or AI analysis may slow down bulk processing
                                        </p>
                                    </div>
                                </motion.div>
                            )}
                        </AnimatePresence>

                        <div className="mt-8">
                            <button
                                type="submit"
                                disabled={loading || !file}
                                className="w-full bg-indigo-600 hover:bg-indigo-700 text-white font-bold py-4 px-6 rounded-xl shadow-lg hover:shadow-xl transition-all disabled:opacity-50 disabled:cursor-not-allowed flex justify-center items-center text-lg"
                            >
                                {loading ? (
                                    <>
                                        <Loader2 className="animate-spin mr-2 h-6 w-6" />
                                        Processing CSV...
                                    </>
                                ) : (
                                    <>
                                        <Upload className="w-5 h-5 mr-2" />
                                        Upload & Shorten URLs
                                    </>
                                )}
                            </button>
                        </div>
                    </div>
                </div>
            </form>
        </div>
    );
};

