import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import { QueryClientProvider } from '@tanstack/react-query'
import { MotionConfig } from 'framer-motion'
import { Toaster } from 'sonner'
import { NextIntlClientProvider } from '@/i18n/compat/client'
import messages from '@/i18n/locales/zh.json'
import { App } from '@/app/App'
import { queryClient } from '@/app/query'
import '@/app/globals.css'
import '@/app/font.css'
import '@/styles/tiptap.scss'
import '@/app/codekin.css'
ReactDOM.createRoot(document.getElementById('root')!).render(
 <React.StrictMode><MotionConfig reducedMotion="user"><NextIntlClientProvider locale="zh" messages={messages}>
 <QueryClientProvider client={queryClient}><BrowserRouter><App/></BrowserRouter></QueryClientProvider>
 <Toaster richColors position="top-center"/>
 </NextIntlClientProvider></MotionConfig></React.StrictMode>
)
