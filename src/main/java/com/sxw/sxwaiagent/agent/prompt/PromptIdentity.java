package com.sxw.sxwaiagent.agent.prompt;

/** Immutable identity of a trusted prompt template. */
public record PromptIdentity(String code, int version, String templateHash) { }
