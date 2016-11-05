package com.thiagomv.timecontroller;

/**
 * Esta classe controla um temporizador para gerar eventos em intervalos de
 * tempos fixos e constantes. Cada atraso de tempo em um frame é compensado no
 * frame seguinte. Caso o atraso for superior ao tempo de um frame, o atraso
 * será considerado a partir do tempo previsto para o último frame e, neste
 * caso, um evento de delay será lançado indicando o número de frames perdidos
 * pelo atraso. Este controlador pode ser operado em um contexto multithread.
 * 
 * @author Thiago Mendes Vieira thiagomv.0301.developer@gmail.com
 * @version 1.0
 */
public final class PeriodicTimeController {
	/** Quantidade de nanosegundos em 1 milisegundo. */
	private static final double NANOSECONDS_IN_ONE_MILISECOND = 1000000.0;

	/** Quantidade de nanosegundos em 1 segundo. */
	private static final double NANOSECONDS_IN_ONE_SECOND = 1000000000.0;

	/** Região crítica para controlar pausas. */
	private final Object lockPause = new Object();

	/** Região crítica para controlar eventos. */
	private final Object lockEvents = new Object();

	/** Tempo entre os ciclos do temporizador, em nanosegundos. */
	private final double timeCycle;

	/** Indica se o temporizador está inicializado. */
	private boolean initialized;

	/** Indica se o temporizador está operando. */
	private boolean running;

	/** Indica se o temporizador está em modo Paused. */
	private boolean paused;

	/** Indica se uma pausa foi requisitada ao temporizador. */
	private boolean pauseRequested;

	/** Indica se um resume foi requisitado ao temporizador. */
	private boolean resumeRequested;

	/**
	 * Referência da Thread dedicada, utilizada para controlar o temporizador.
	 */
	private Thread t;

	/** Capturador de eventos de ITimeEvent. */
	private PeriodicTimeEventListener timeHandle;

	/** Tempo da última atualização. **/
	private long lastTime;

	/**
	 * Atraso na última atualização em relação ao tempo ideal que deveria ter.
	 **/
	private double delayLastTime;

	/** Tempo real atual. **/
	private long nowTime;

	/** Tempo real decorrido. **/
	private double elapsedTime;

	/** Tempo real no momento que foi pausado **/
	private long pausedTime;

	/**
	 * Inicializa o controlador de tempo para operar com a taxa de FPS
	 * informada.
	 * 
	 * @param fps
	 *            Quantidade de frames por segundo que este temporizador deverá
	 *            suportar.
	 */
	public PeriodicTimeController(int fps) {
		this.timeCycle = NANOSECONDS_IN_ONE_SECOND / fps;
	}

	/**
	 * Estabelece o capturador de eventos deste controlador. Não é seguro chamar
	 * este método quando o temporizador estiver em estado Resumed.
	 * 
	 * @param handle
	 *            Capturador de eventos de ITimeEvent.
	 */
	public final synchronized void setTimeHandle(PeriodicTimeEventListener handle) {
		synchronized (lockEvents) {
			this.timeHandle = handle;
		}
	}

	/**
	 * Inicializa o temporizador (caso ainda não tenha sido inicializado). O
	 * temporizador imediatamente irá para o estado de Paused.
	 */
	public final synchronized void create() {
		if (!initialized) {
			initialized = true;
			running = true;
			pauseRequested = true;
			paused = false;
			resumeRequested = false;
			t = new Thread(new Runnable() {

				public void run() {
					process();
				}
			});
			t.start();
		}
	}

	/**
	 * Finaliza a execução do temporizador e seus recursos. Posteriormente o
	 * controlador pode ser recriado pelo método {@code create()}. Se o
	 * temporizador estiver em modo Paused ele irá imediatamente para o modo
	 * Terminado. Caso o temporizador esteja em modo Resumed será solicitado que
	 * entre em modo de Paused e, em seguida, para o modo Terminado. Este método
	 * aguarda o encerramento completo do temporizador antes de retornar.
	 */
	public final synchronized void destroy() {
		if (initialized) {
			// Espera o temporizador terminar o que estiver fazendo.
			doPause();
			running = false;
			// Coloca em estado de Resumed apenas para finalizar o loop.
			doResume();
			try {
				// Aguarda a finalização do loop.
				t.join();
			} catch (InterruptedException e) {
				throw new RuntimeException("Erro em TimeController.onDestroy()");
			}
			t = null;
			initialized = false;
		}
	}

	/**
	 * Solicita a parada do temporizador e espera até que esteja em estado de
	 * Paused.
	 */
	public final synchronized void pause() {
		doPause();
	}

	private final void doPause() {
		synchronized (lockPause) {
			if (running && !paused) {
				pauseRequested = true;
				while (!paused)
					try {
						// Espera notificação de Paused do temporizador.
						lockPause.wait();
					} catch (InterruptedException e) {
						throw new RuntimeException("Erro em TimeController.onPause()");
					}
			}
		}
	}

	/**
	 * Solicita que o temporizador continue desde a contagem de quando foi
	 * pausado. Espera até que o controlador esteja em estado de Resumed.
	 */
	public final synchronized void resume() {
		doResume();
	}

	private final void doResume() {
		synchronized (lockPause) {
			/*
			 * Na inicialização do temporizador ele imediatamente passa para
			 * modo de Paused. Mas pode acontecer de esta função ser chamada
			 * antes da passagem para o modo Paused. Por isso deve ser
			 * verificado.
			 */
			if (pauseRequested) {
				while (!paused)
					try {
						// Espera notificação de Paused do temporizador.
						lockPause.wait();
					} catch (InterruptedException e) {
						throw new RuntimeException("Erro em TimeController.onPause()");
					}
			}

			if (paused) {
				resumeRequested = true;
				// Notifica temporizador para ficar Resumed.
				lockPause.notify();
				while (paused) {
					try {
						// Espera notificação de Resumed do temporizador.
						lockPause.wait();
					} catch (InterruptedException e) {
						throw new RuntimeException("Erro em TimeController.onResume()");
					}
				}
			}
		}
	}

	/**
	 * Fluxo executado pela Thread do temporizador.
	 */
	private final void process() {
		lastTime = System.nanoTime();
		delayLastTime = 0.0;

		while (running) {
			// Trata possíveis solicitações de pausa.
			verifyPause();
			if (!running) {
				break;
			}

			// Calcula o tempo corrido desde a última atualização.
			nowTime = System.nanoTime();
			elapsedTime = ((double) (nowTime - lastTime)) + delayLastTime;

			if (elapsedTime >= timeCycle) {
				// Gerar eventos!
				synchronized (lockEvents) {
					generateEvents(elapsedTime - delayLastTime);

					delayLastTime = elapsedTime - timeCycle;
					if (delayLastTime >= timeCycle) {
						// Super delay!
						onSuperdelay(delayLastTime);
					}
				}

				// Atualiza o tempo da última atualização.
				lastTime = nowTime;
			} else {
				// Durma mais um pouco!
				try {
					Thread.sleep((long) ((timeCycle - elapsedTime) / NANOSECONDS_IN_ONE_MILISECOND));
				} catch (InterruptedException e) {
					throw new RuntimeException("Erro em TimeController.run()");
				}
			}
		}
	}

	/**
	 * Verifica possíveis solicitações de pausa. Se uma pausa foi solicitada a
	 * Thread irá esperar até que seja solicitada sua continuação.
	 */
	private final void verifyPause() {
		synchronized (lockPause) {
			if (pauseRequested) {
				pausedTime = System.nanoTime();
				pauseRequested = false;
				paused = true;
				// Notifica onPause() que o temporizador está Paused.
				lockPause.notify();
				while (!resumeRequested)
					try {
						lockPause.wait();// Espera onResume().
					} catch (InterruptedException e) {
						throw new RuntimeException("Erro em TimeController.verifyPause()");
					}
				resumeRequested = false;
				paused = false;
				// Notifica onResume() que o temporizador está Resumed.
				lockPause.notify();

				long totalTimePaused = System.nanoTime() - pausedTime;
				lastTime += totalTimePaused;
			}
		}
	}

	/**
	 * Este método é utilizado para lançar um evento indicando a quantidade de
	 * frames perdidos pelo atraso.
	 * 
	 * @param delay
	 *            Tempo total de atraso.
	 */
	private final void onSuperdelay(double delay) {
		delayLastTime = (delay % timeCycle);

		if (timeHandle != null) {
			int frames = (int) Math.floor(delay / timeCycle);
			timeHandle.onFramesLost(frames);
		}
	}

	/**
	 * Este método é utilizado para lançar um evento indicando o tempo decorrido
	 * desde a última atualização.
	 * 
	 * @param time
	 */
	private final void generateEvents(double time) {
		if (timeHandle != null) {
			timeHandle.onTimeEvent(time);
		}
	}

	/**
	 * Retorna o tempo de ciclo ideal para cada frame.
	 * 
	 * @return Tempo de ciclo ideal para cada frame.
	 */
	public double getTimeCycle() {
		return this.timeCycle;
	}
}