- ask for a plan first : https://code.claude.com/docs/en/best-practices

- Run /init to generate a starter CLAUDE.md file based on your current project structure, then refine over time.
- 
Claude.md, Agent.md:

"I want to build [brief description]. Interview me in detail using the AskUserQuestion tool."


"Course-correct early and often" (esc-esc)
https://code.claude.com/docs/en/best-practices


"Use subagents for investigation"
Delegate research with "use subagents to investigate X". They explore in a separate context, keeping your main conversation clean for implementation.
Since context is your fundamental constraint, use subagents to keep research out of it. When Claude researches a codebase it reads lots of files, all of which consume your context. Subagents run in separate context windows and report back summaries:
https://code.claude.com/docs/en/best-practices


- usage de ideas, todo, plan
- instructions:

Comment je veux interagir avec l'environement de code.

ADR
Architecture decision report :
à rédiger à chaque grande décision

TODO.md plus détaillé
Plan.md plus large, court

Ideas.md

- skills:


fais un audit de sécurité, vitesse, etc...
