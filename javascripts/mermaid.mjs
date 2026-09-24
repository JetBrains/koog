import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs';
import elkLayouts from 'https://cdn.jsdelivr.net/npm/@mermaid-js/layout-elk@0/dist/mermaid-layout-elk.esm.min.mjs';

mermaid.registerLayoutLoaders(elkLayouts);
mermaid.initialize({
    startOnLoad: false,
    securityLevel: "loose",
    layout: "elk",
});

// Important: necessary to make it visible to Material for MkDocs
window.mermaid = mermaid;
