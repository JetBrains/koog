import './Greeting.css';

import { useState } from 'react';
import { JSLogo } from '../JSLogo/JSLogo.tsx';
import {KoogAgent, AgentConfiguration} from 'koogelis';
import type { AnimationEvent } from 'react';

export function Greeting() {
  const apiKey = ''; // not needed for local
  const modelId = 'local'; // can also be gemini-2.0-flash or gemini-2.5-flash-lite or gemini-2.5-pro
  const agentConfig = new AgentConfiguration(
      'Joker',
      new AgentConfiguration.Llm(
          modelId,
          '',
          apiKey,
          new AgentConfiguration.LlmParams(1.0, 1000),
      ),
      AgentConfiguration.AgentStrategy.SINGLE_RUN,
      'You are a helpful assistant.',
      [],
      10,
      true,
      null,
      null
  );
  const koogAgent = KoogAgent.KoogFactory.create(agentConfig);
  const [isVisible, setIsVisible] = useState<boolean>(false);
  const [isAnimating, setIsAnimating] = useState<boolean>(false);
  const [joke, setJoke] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(false);

  const fetchJoke = async () => {
    try {
      setLoading(true);
      const text = await koogAgent.invoke('Gimme a good joke about JS/TS please');
      setJoke(text);
    } finally {
      setLoading(false);
    }
  };

  const handleClick = () => {
    if (isVisible) {
      setIsAnimating(true);
    } else {
      setIsVisible(true);
      // fetch a new joke when showing the greeting
      fetchJoke();
    }
  };

  const handleAnimationEnd = (event: AnimationEvent<HTMLDivElement>) => {
    if (event.animationName === 'fadeOut') {
      setIsVisible(false);
      setIsAnimating(false);
    }
  };

  return (
    <div className="greeting-container">
      <button onClick={handleClick} className="greeting-button">
        Tell me a joke about JavaScript!
      </button>

      {isVisible && (
        <div className={isAnimating ? 'greeting-content fade-out' : 'greeting-content'} onAnimationEnd={handleAnimationEnd}>
          <JSLogo />
          <div>React: {loading ? 'Loading…' : joke}</div>
        </div>
      )}
    </div>
  );
}
