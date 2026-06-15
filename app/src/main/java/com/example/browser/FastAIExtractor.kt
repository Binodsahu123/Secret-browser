package com.example.browser

object FastAIExtractor {
    const val JS_EXTRACT_SCRIPT = """
        (function() {
            try {
                var text = document.body ? document.body.innerText : '';
                var title = document.title || '';
                return JSON.stringify({ title: title, content: text.substring(0, 5000) });
            } catch(e) {
                return JSON.stringify({ title: document.title || '', content: '' });
            }
        })()
    """
}
