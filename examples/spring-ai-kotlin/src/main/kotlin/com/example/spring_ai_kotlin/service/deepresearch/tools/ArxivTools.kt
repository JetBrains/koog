package com.example.spring_ai_kotlin.service.deepresearch.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.example.spring_ai_kotlin.service.deepresearch.model.Note
import com.example.spring_ai_kotlin.service.deepresearch.model.Paper

@LLMDescription("Tools for searching arXiv papers and extracting structured research notes.")
class ArxivTools : ToolSet {

    @Tool
    @LLMDescription("Search arXiv for a topic and return structured paper metadata.")
    @Suppress("unused")
    suspend fun searchArxiv(
        @LLMDescription("The arXiv search query.")
        query: String
    ): List<Paper> {
        return listOf(
            Paper(
                id = "1706.03762",
                title = "Attention Is All You Need",
                authors = listOf("Ashish Vaswani", "Noam Shazeer", "Niki Parmar", "Jakob Uszkoreit", "Llion Jones", "Aidan N. Gomez", "Lukasz Kaiser", "Illia Polosukhin"),
                abstract = "The dominant sequence transduction models are based on complex recurrent or convolutional neural networks in an encoder-decoder configuration. We propose a new simple network architecture, the Transformer, based solely on attention mechanisms, dispensing with recurrence and convolutions entirely.",
                url = "https://arxiv.org/abs/1706.03762",
                published = "2017-06-12"
            ),
            Paper(
                id = "1810.04805",
                title = "BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding",
                authors = listOf("Jacob Devlin", "Ming-Wei Chang", "Kenton Lee", "Kristina Toutanova"),
                abstract = "We introduce a new language representation model called BERT, designed to pre-train deep bidirectional representations from unlabeled text by jointly conditioning on both left and right context in all layers.",
                url = "https://arxiv.org/abs/1810.04805",
                published = "2018-10-11"
            ),
            Paper(
                id = "2005.14165",
                title = "Language Models are Few-Shot Learners",
                authors = listOf("Tom B. Brown", "Benjamin Mann", "Nick Ryder"),
                abstract = "We show that scaling up language models greatly improves task-agnostic, few-shot performance, sometimes even reaching competitiveness with prior state-of-the-art fine-tuning approaches.",
                url = "https://arxiv.org/abs/2005.14165",
                published = "2020-05-28"
            )
        )
    }

    @Tool
    @LLMDescription("Get a paper abstract from arXiv by paper id.")
    @Suppress("unused")
    suspend fun getAbstract(
        @LLMDescription("The arXiv paper id, for example 2401.01234.")
        arxivId: String
    ): String {
        return when (arxivId) {
            "1706.03762" -> "The dominant sequence transduction models are based on complex recurrent or convolutional neural networks in an encoder-decoder configuration. We propose a new simple network architecture, the Transformer, based solely on attention mechanisms, dispensing with recurrence and convolutions entirely."
            "1810.04805" -> "We introduce a new language representation model called BERT, designed to pre-train deep bidirectional representations from unlabeled text by jointly conditioning on both left and right context in all layers."
            "2005.14165" -> "We show that scaling up language models greatly improves task-agnostic, few-shot performance, sometimes even reaching competitiveness with prior state-of-the-art fine-tuning approaches."
            else -> "Abstract not available for paper $arxivId."
        }
    }

    @Tool
    @LLMDescription("Extract a structured literature note from a paper title and abstract.")
    @Suppress("unused")
    suspend fun extractNote(
        @LLMDescription("The paper to summarize into a note.")
        paper: Paper
    ): Note {
        return Note(
            paperId = paper.id,
            problem = "Addressing limitations in sequence transduction and language understanding using ${paper.title}.",
            method = "Proposed architecture based on the approach described in '${paper.title}'.",
            findings = "Achieved state-of-the-art results on standard benchmarks at the time of publication (${paper.published}).",
            limitations = "Computational cost and scalability concerns; evaluation limited to benchmarks available at the time."
        )
    }
}
