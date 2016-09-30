package org.hhn.topicgrouper.lda.validation;

import gnu.trove.iterator.TIntIterator;

import java.util.Random;

import org.hhn.topicgrouper.doc.Document;
import org.hhn.topicgrouper.doc.DocumentProvider;
import org.hhn.topicgrouper.lda.impl.LDAGibbsSampler;
import org.hhn.topicgrouper.tg.validation.TGPerplexityCalculator;

public abstract class AbstractLDAPerplexityCalculator<T> {
	protected final boolean bowFactor;
	protected double[] ptd;
	protected final ComputationHelper helper;

	public AbstractLDAPerplexityCalculator(boolean bowFactor) {
		this.bowFactor = bowFactor;
		helper = initComputationHelper();
	}

	protected ComputationHelper initComputationHelper() {
		return new ComputationHelper();
	}

	public double computePerplexity(DocumentProvider<T> testDocumentProvider,
			LDAGibbsSampler<T> sampler) {
		if (ptd == null || ptd.length != sampler.getNTopics()) {
			ptd = new double[sampler.getNTopics()];
		}

		return helper.computePerplexity(testDocumentProvider, sampler);
	}

	protected double computeLogProbability(LDAGibbsSampler<T> sampler,
			Document<T> d, int dSize, int dIndex) {
		DocumentProvider<T> provider = sampler.getDocumentProvider();
		double res = bowFactor ? TGPerplexityCalculator.logFacN(dSize) : 0;

		// update ptd for d
		updatePtd(sampler, d, dSize, dIndex);

		TIntIterator it = d.getWordIndices().iterator();
		while (it.hasNext()) {
			int index = it.next();
			T word = d.getProvider().getWord(index);
			int sIndex = provider.getIndex(word);
			// Ensure the word is in the training vocabulary.
			if (sIndex >= 0) {
				int wordFr = d.getWordFrequency(index);
				if (wordFr > 0) {
					if (bowFactor) {
						res -= TGPerplexityCalculator.logFacN(wordFr);
					}
					res += wordFr
							* computeWordLogProbability(sampler, sIndex, d);
				}
			}
		}
		return res;
	}

	protected abstract void updatePtd(LDAGibbsSampler<T> sampler,
			Document<T> d, int dSize, int dIndex);

	private double computeWordLogProbability(LDAGibbsSampler<T> sampler,
			int sIndex, Document<T> d) {
		double sum = 0;
		for (int i = 0; i < ptd.length; i++) {
			if (sampler.getTopicFrCount(i) > 0) { // To avoid division by zero.
													// Also correct: If a topic
													// has zero probability
													// (zero frequency), it
													// cannot be allocated to
													// produce a word.
				sum += ((double) sampler.getTopicWordAssignmentCount(i, sIndex))
						/ sampler.getTopicFrCount(i) * ptd[i];
			}
		}
		return Math.log(sum);
	}

	protected class ComputationHelper {
		public double computePerplexity(
				DocumentProvider<T> testDocumentProvider,
				LDAGibbsSampler<T> sampler) {
			DocumentProvider<T> provider = sampler.getDocumentProvider();
			double sumA = 0;
			long sumB = 0;
			// Compute the document size excluding words not in the training
			// vocabulary.
			// (Therefore cannot use d.size() ...)
			int i = 0;
			for (Document<T> d : testDocumentProvider.getDocuments()) {
				int dSize = 0;

				TIntIterator it = d.getWordIndices().iterator();
				while (it.hasNext()) {
					int index = it.next();
					T word = testDocumentProvider.getWord(index);
					int sIndex = provider.getIndex(word);
					if (sIndex >= 0) {
						dSize += d.getWordFrequency(index);
					}
				}

				if (dSize > 0) {
					sumA += computeLogProbability(sampler, d, dSize, i);
					sumB += dSize;
				}
				i++;
			}
			return Math.exp(-sumA / sumB);
		}
	}

	protected abstract class OneWordComputationHelper extends ComputationHelper {
		@Override
		public double computePerplexity(
				DocumentProvider<T> testDocumentProvider,
				LDAGibbsSampler<T> sampler) {
			DocumentProvider<T> provider = sampler.getDocumentProvider();
			double sumA = 0;
			long sumB = 0;
			int i = 0;
			for (Document<T> d : testDocumentProvider.getDocuments()) {
				int dSize = 0;
				TIntIterator it = d.getWordIndices().iterator();

				// Choose a random word to predict from the test document
				// (uniform).
				int positionOfHeldOutWord = getRandom().nextInt(d.getSize());
				int handledWords = 0;
				int heldOutWordIndex = -1;

				while (it.hasNext()) {
					int index = it.next();
					T word = testDocumentProvider.getWord(index);
					int sIndex = provider.getIndex(word);
					if (sIndex >= 0) {
						dSize += d.getWordFrequency(index);
					}
					// Get the index of the word to predict with regard to
					// solution.
					handledWords += d.getWordFrequency(sIndex);
					if (handledWords >= positionOfHeldOutWord
							&& heldOutWordIndex == -1) {
						// Is it a word from the training vocubulary?
						if (sIndex >= 0) {
							heldOutWordIndex = sIndex;
						}
					}
				}
				if (heldOutWordIndex == -1) {
					// No matching word from the training vocubulary found.
					// Restart at the beginning.
					while (it.hasNext()) {
						int index = it.next();
						T word = testDocumentProvider.getWord(index);
						int sIndex = provider.getIndex(word);
						if (sIndex >= 0) {
							heldOutWordIndex = sIndex;
							break;
						}
					}
				}
				if (heldOutWordIndex >= 0) {
					// Exclude word to predict.
					// update ptd for d
					updatePtd(sampler, d, dSize, i);

					sumA += computeWordLogProbability(sampler,
							heldOutWordIndex, d);
					sumB++;
				} else {
					// This means no word from test document occurs in the
					// training
					// vocabulary. Just do nothing and omit this test document.
					// (Should be rare...)
				}
				i++;
			}
			return Math.exp(-sumA / sumB);
		}

		protected abstract Random getRandom();
	}
}
