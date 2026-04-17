package com.example.spring_ai_java.service.deepresearch;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;
import com.example.spring_ai_java.service.deepresearch.model.Note;
import com.example.spring_ai_java.service.deepresearch.model.Paper;

import java.util.List;

@LLMDescription("Tools for searching arXiv papers and extracting structured research notes.")
public class ArxivTools implements ToolSet {

    @Tool
    @LLMDescription("Search arXiv for a topic and return structured paper metadata.")
    public List<Paper> searchArxiv(
        @LLMDescription("The arXiv search query.") String query
    ) {
        return List.of(
            new Paper(
                "1706.03762",
                "Attention Is All You Need",
                List.of("Ashish Vaswani", "Noam Shazeer", "Niki Parmar", "Jakob Uszkoreit",
                    "Llion Jones", "Aidan N. Gomez", "Lukasz Kaiser", "Illia Polosukhin"),
                "The dominant sequence transduction models are based on complex recurrent or convolutional " +
                    "neural networks in an encoder-decoder configuration. We propose a new simple network " +
                    "architecture, the Transformer, based solely on attention mechanisms.",
                "https://arxiv.org/abs/1706.03762",
                "2017-06-12"
            ),
            new Paper(
                "1810.04805",
                "BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding",
                List.of("Jacob Devlin", "Ming-Wei Chang", "Kenton Lee", "Kristina Toutanova"),
                "We introduce a new language representation model called BERT, designed to pre-train deep " +
                    "bidirectional representations from unlabeled text by jointly conditioning on both left " +
                    "and right context in all layers.",
                "https://arxiv.org/abs/1810.04805",
                "2018-10-11"
            ),
            new Paper(
                "2005.14165",
                "Language Models are Few-Shot Learners",
                List.of("Tom B. Brown", "Benjamin Mann", "Nick Ryder"),
                "We show that scaling up language models greatly improves task-agnostic, few-shot performance, " +
                    "sometimes even reaching competitiveness with prior state-of-the-art fine-tuning approaches.",
                "https://arxiv.org/abs/2005.14165",
                "2020-05-28"
            )
        );
    }

    @Tool
    @LLMDescription("Get a paper abstract from arXiv by paper id.")
    public String getAbstract(
        @LLMDescription("The arXiv paper id, for example 2401.01234.") String arxivId
    ) {
        return switch (arxivId) {
            case "1706.03762" ->
                "The dominant sequence transduction models are based on complex recurrent or convolutional " +
                    "neural networks. We propose the Transformer, based solely on attention mechanisms.";
            case "1810.04805" ->
                "We introduce BERT, designed to pre-train deep bidirectional representations from unlabeled " +
                    "text by jointly conditioning on both left and right context in all layers.";
            case "2005.14165" ->
                "We show that scaling up language models greatly improves task-agnostic, few-shot performance.";
            default -> "Abstract not available for paper " + arxivId + ".";
        };
    }

    @Tool
    @LLMDescription("Extract a structured literature note from a paper title and abstract.")
    public Note extractNote(
        @LLMDescription("The paper to summarize into a note.") Paper paper
    ) {
        return new Note(
            paper.getId(),
            "Addressing limitations in sequence transduction and language understanding using " + paper.getTitle() + ".",
            "Proposed architecture based on the approach described in '" + paper.getTitle() + "'.",
            "Achieved state-of-the-art results on standard benchmarks at the time of publication (" + paper.getPublished() + ").",
            "Computational cost and scalability concerns; evaluation limited to benchmarks available at the time."
        );
    }
}
