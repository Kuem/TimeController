package com.thiagomv.timecontroller;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

import org.hamcrest.CoreMatchers;
import org.junit.Test;

public class PeriodicTimeControllerTest {
	private static final int COMMON_FPS = 60;
	private static final int MILLISECONDS_IN_ONE_SECOND = 1000;
	private static final int NANOSECONDS_IN_ONE_SECOND = 1000000000;
	private static final int NANOSECONDS_IN_ONE_MILLISECOND = 1000000;

	/**
	 * Verifica que a criação e finalização do temporizador não lança eventos.
	 */
	@Test
	public void criarTemporizadorNaoLancaEventos() {
		PeriodicTimeController timeController = new PeriodicTimeController(COMMON_FPS);
		timeController.setTimeHandle(new PeriodicTimeEventListener() {

			public void onTimeEvent(double elapsedNanoseconds) {
				fail();
			}

			public void onFramesLost(int numFrames) {
				fail();
			}
		});
		timeController.create();
		try {
			Thread.sleep(MILLISECONDS_IN_ONE_SECOND);
		} catch (InterruptedException e) {
			fail();
		}
		timeController.destroy();
	}

	/**
	 * Verifica a precisão do temporizador na contagem de frames gerados de
	 * acordo com o tempo corrido. Verifica também a precisão de velocidade de
	 * acionamento e pausa do temporizador em tempo hábil.
	 */
	@Test
	public void temporizadorLancaEventosComPrecisaoCorreta() {
		PeriodicTimeController timeController = new PeriodicTimeController(COMMON_FPS);

		Map<String, Integer> mapaContadorEventos = new HashMap<>();
		Object lock = new Object();
		mapaContadorEventos.put("onTimeEvent", 0);
		mapaContadorEventos.put("onFramesLost", 0);
		timeController.setTimeHandle(new PeriodicTimeEventListener() {

			public void onTimeEvent(double elapsedNanoseconds) {
				synchronized (lock) {
					mapaContadorEventos.put("onTimeEvent", mapaContadorEventos.get("onTimeEvent") + 1);
				}
			}

			public void onFramesLost(int numFrames) {
				synchronized (lock) {
					mapaContadorEventos.put("onFramesLost", mapaContadorEventos.get("onFramesLost") + 1);
				}
			}
		});

		timeController.create();
		try {
			Thread.sleep(MILLISECONDS_IN_ONE_SECOND);
		} catch (InterruptedException e) {
			fail();
		}

		int numSeconds = 2;
		long tempoEsperadoMiliseconds = MILLISECONDS_IN_ONE_SECOND * numSeconds;
		long tempoEsperadoNanoseconds = tempoEsperadoMiliseconds * NANOSECONDS_IN_ONE_MILLISECOND;

		long lastTime = System.nanoTime();

		// Aciona o temporizador.
		timeController.resume();
		try {
			Thread.sleep(tempoEsperadoMiliseconds);
		} catch (InterruptedException e) {
			fail();
		}

		// Pausa o temporizador.
		timeController.pause();
		long tempoTotal = System.nanoTime() - lastTime;

		/*
		 * Verifica que a margem de erro de precisão do temporizador seja menor
		 * que 5% do tempo de 1 ciclo.
		 */
		float precisao = 0.05F;

		assertTrue("A margem de erro de precisão do temporizador precisa ser menor que " + (100 * precisao)
				+ "% de um ciclo." + " Erro de precisão gerado: " + Math.abs(tempoTotal - tempoEsperadoNanoseconds)
				+ ". Erro de precisão máximo esperado: " + NANOSECONDS_IN_ONE_SECOND * precisao + ".",
				Math.abs(tempoTotal - tempoEsperadoNanoseconds) < NANOSECONDS_IN_ONE_SECOND * precisao);

		int timeEvents = mapaContadorEventos.get("onTimeEvent");
		int framesLost = mapaContadorEventos.get("onFramesLost");

		assertThat("Nenhum frame pode ser perdido quando não há processamento excessivo.", framesLost,
				CoreMatchers.equalTo(0));

		/*
		 * Verifica se a quantidade de eventos gerados é compatível com o tempo
		 * corrido. Pode ser que alguns frames deixe de ser lançado antes da
		 * última vez que o temporizador foi pausado. Como o encerramento do
		 * temporizador não lança este evento precisamos ter o cuidado de
		 * considerar essa possível perda de evento que não é contabilizada.
		 * Portanto a margem de erro para a contagem de frames gerados deve ser
		 * no máximo 1 frame, de acordo com o cenário descrito acima.
		 */
		assertTrue(
				"A quantidade de eventos gerados deve ser compatível com o tempo corrido. Erro de precisao gerado: "
						+ Math.abs((timeEvents) - (numSeconds * COMMON_FPS)) + ". Erro máximo esperado: " + 1 + ".",
				Math.abs((timeEvents) - (numSeconds * COMMON_FPS)) < 1);

		timeController.destroy();
	}

	/**
	 * Verifica se a quantidade de frames perdidos e eventos de frames gerados
	 * são compatíveis com o tempo corrido.
	 */
	@Test
	public void verificaFramesPerdidos() {
		PeriodicTimeController timeController = new PeriodicTimeController(COMMON_FPS);

		Map<String, Integer> mapaContadorEventos = new HashMap<>();
		Object lock = new Object();
		mapaContadorEventos.put("onTimeEvent", 0);
		mapaContadorEventos.put("onFramesLost", 0);

		int framesParaSeremPerdidos = 3;

		timeController.setTimeHandle(new PeriodicTimeEventListener() {

			public void onTimeEvent(double elapsedNanoseconds) {
				synchronized (lock) {
					mapaContadorEventos.put("onTimeEvent", mapaContadorEventos.get("onTimeEvent") + 1);

					// Processamento demorado para causar a perda de 2 frames...
					try {
						Thread.sleep((int) (framesParaSeremPerdidos
								* ((float) MILLISECONDS_IN_ONE_SECOND / (float) COMMON_FPS)));
					} catch (InterruptedException e) {
						fail();
					}
				}
			}

			public void onFramesLost(int numFrames) {
				synchronized (lock) {
					mapaContadorEventos.put("onFramesLost", mapaContadorEventos.get("onFramesLost") + numFrames);
				}
			}
		});

		timeController.create();
		try {
			Thread.sleep(MILLISECONDS_IN_ONE_SECOND);
		} catch (InterruptedException e) {
			fail();
		}

		int numSeconds = 2;
		long tempoEsperadoMiliseconds = MILLISECONDS_IN_ONE_SECOND * numSeconds;
		long tempoEsperadoNanoseconds = tempoEsperadoMiliseconds * NANOSECONDS_IN_ONE_MILLISECOND;

		long lastTime = System.nanoTime();

		// Aciona o temporizador.
		timeController.resume();
		try {
			Thread.sleep(tempoEsperadoMiliseconds);
		} catch (InterruptedException e) {
			fail();
		}

		// Pausa o temporizador.
		timeController.pause();
		long tempoTotal = System.nanoTime() - lastTime;

		/*
		 * Verifica que a margem de erro de precisão do temporizador seja menor
		 * que 5% do tempo de 1 ciclo.
		 */
		float precisao = 0.05F;

		assertTrue("A margem de erro de precisão do temporizador precisa ser menor que " + (100 * precisao)
				+ "% de um ciclo." + " Erro de precisão gerado: " + Math.abs(tempoTotal - tempoEsperadoNanoseconds)
				+ ". Erro de precisão máximo esperado: " + NANOSECONDS_IN_ONE_SECOND * precisao + ".",
				Math.abs(tempoTotal - tempoEsperadoNanoseconds) < NANOSECONDS_IN_ONE_SECOND * precisao);

		int timeEvents = mapaContadorEventos.get("onTimeEvent");
		int framesLost = mapaContadorEventos.get("onFramesLost");

		assertTrue("Algum frame deve ser perdido quando o processamento ultrapassa o tempo de um frame.",
				framesLost > 0);

		/*
		 * Verifica se a quantidade de eventos gerados e perdidos é compatível
		 * com o tempo corrido. Pode ser que alguns frames sejam perdidos antes
		 * da última vez que o temporizador foi pausado. Como o encerramento do
		 * temporizador não lança estes eventos precisamos ter o cuidado de
		 * considerar essa possível perda de eventos que não são contabilizadas.
		 * Portanto a margem de erro para a contagem de frames perdidos deve ser
		 * no máximo a quantidade máxima de frames que possam ser perdidos em um
		 * único processamento.
		 */
		assertTrue(
				"A quantidade de eventos gerados e perdidos deve ser compatível com o tempo corrido. Erro de precisao gerado: "
						+ Math.abs((timeEvents + framesLost) - (numSeconds * COMMON_FPS)) + ". Erro máximo esperado: "
						+ framesParaSeremPerdidos + ".",
				Math.abs((timeEvents + framesLost) - (numSeconds * COMMON_FPS)) < framesParaSeremPerdidos);

		timeController.destroy();
	}
}
