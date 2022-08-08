<?php

namespace App\EventSubscriber;

use App\Annotation\SzApiLog;
use Doctrine\Common\Annotations\Reader;
use Psr\Log\LoggerInterface;
use Symfony\Component\EventDispatcher\EventSubscriberInterface;
use Symfony\Component\HttpKernel\Event\FilterControllerEvent;
use Symfony\Component\HttpKernel\Event\FinishRequestEvent;
use Symfony\Component\HttpKernel\Event\PostResponseEvent;
use Symfony\Component\HttpKernel\KernelEvents;
use Symfony\Component\Stopwatch\Stopwatch;
use Symfony\Component\Stopwatch\StopwatchEvent;

class SzApiLogSubscriber implements EventSubscriberInterface
{
    /**
     * @var Reader
     */
    private $reader;

    /**
     * @var bool
     */
    private $underTrack = false;

    /**
     * @var Stopwatch
     */
    private $stopwatch;
    /**
     * @var string
     */
    private $swEventName;

    /**
     * @var string
     */
    private $requestUid;

    /**
     * @var LoggerInterface
     */
    private $logger;
    /**
     * @var \DateTimeImmutable
     */
    private $startDateTime;
    /**
     * @var string
     */
    private $requestUri;
    /**
     * @var string
     */
    private $host;
    /**
     * @var \ArrayIterator
     */
    private $requestHeaders;
    /**
     * @var \ArrayIterator
     */
    private $responseHeaders;
    /**
     * @var string
     */
    private $requestBody;
    /**
     * @var string
     */
    private $responseBody;
    /**
     * @var float|int
     */
    private $duration;
    /**
     * @var \DateTimeImmutable
     */
    private $endDateTime;

    public function __construct(Reader $reader, Stopwatch $stopwatch, LoggerInterface $logger)
    {
        $this->reader = $reader;
        $this->stopwatch = $stopwatch;
        $this->logger = $logger;
    }

    public static function getSubscribedEvents(): array
    {
        return [
            //Save request Api info
            KernelEvents::CONTROLLER => 'onKernelController',
            //Log all info at end
            KernelEvents::TERMINATE => 'onKernelTerminate',
        ];
    }

    /**
     * @throws \ReflectionException
     */
    public function onKernelController(FilterControllerEvent $event): void
    {
        if (!$event->isMasterRequest()) {
            return;
        }

        $class = get_class($event->getController()[0]);
        $methodAction = $event->getController()[1];

        $reflectionMethod = new \ReflectionMethod($class, $methodAction);

        if (!$annotation = $this->reader->getMethodAnnotation($reflectionMethod, SzApiLog::class)) {
            return;
        }
        $request = $event->getRequest();

        $this->keepRequestData($class, $methodAction, $request);
        $this->setUnderTrack(true);

        $this->stopwatch->start($this->getSwEventName());
    }

    /**
     * @param string $class
     * @param $methodAction
     * @param \Symfony\Component\HttpFoundation\Request $request
     * @return void
     */
    public function keepRequestData(string $class, $methodAction, \Symfony\Component\HttpFoundation\Request $request): void
    {
        $this->setRequestUid(uniqid());
        $this->setSwEventName($class, $methodAction);
        $this->setStartDateTime(new \DateTimeImmutable());
        $this->setUri($request->getRequestUri());
        $this->setHost($request->getHost());
        $this->setRequestHeaders($request->headers->getIterator());
        $this->setRequestBody("" . $request->getContent());
    }


    //IO blocking must do at the end
    public function onKernelTerminate(PostResponseEvent $event): void
    {
        if (!$event->isMasterRequest() || !$this->isUnderTrack()) {
            return;
        }

        $swEvent = $this->stopwatch->stop($this->swEventName);
        $this->keepResponseData($event, $swEvent);

        //Si vous voulez rendre l'application lente et garantir l'écriture du log de la requête, placez le log "Api Request" dans le onKernelController
        $this->logRequest();

        $this->logResponse();
    }



    /**
     * @param string $requestUid
     * @return SzApiLogSubscriber
     */
    public function setRequestUid(string $requestUid): SzApiLogSubscriber
    {
        $this->requestUid = $requestUid;

        return $this;
    }

    /**
     * @param bool $underTrack
     * @return SzApiLogSubscriber
     */
    public function setUnderTrack(bool $underTrack): SzApiLogSubscriber
    {
        $this->underTrack = $underTrack;

        return $this;
    }

    /**
     * @return bool
     */
    public function isUnderTrack(): bool
    {
        return $this->underTrack;
    }

    /**
     * @param string $swEventName
     * @return SzApiLogSubscriber
     */
    public function setSwEventName(string $class, string $methodAction): SzApiLogSubscriber
    {
        $this->swEventName = sprintf('%s::%s', $class, $methodAction);

        return $this;
    }

    /**
     * @return string
     */
    public function getSwEventName(): string
    {
        return $this->swEventName;
    }

    private function setStartDateTime(\DateTimeImmutable $param)
    {
        $this->startDateTime = $param;
    }

    private function setUri(string $getRequestUri)
    {
        $this->requestUri = $getRequestUri;
    }

    private function setHost(string $getRequestUri)
    {
        $this->host = $getRequestUri;
    }

    /**
     * @return Reader
     */
    public function getReader(): Reader
    {
        return $this->reader;
    }

    /**
     * @param Reader $reader
     * @return SzApiLogSubscriber
     */
    public function setReader(Reader $reader): SzApiLogSubscriber
    {
        $this->reader = $reader;
        return $this;
    }

    /**
     * @return Stopwatch
     */
    public function getStopwatch(): Stopwatch
    {
        return $this->stopwatch;
    }

    /**
     * @param Stopwatch $stopwatch
     * @return SzApiLogSubscriber
     */
    public function setStopwatch(Stopwatch $stopwatch): SzApiLogSubscriber
    {
        $this->stopwatch = $stopwatch;
        return $this;
    }

    /**
     * @return LoggerInterface
     */
    public function getLogger(): LoggerInterface
    {
        return $this->logger;
    }

    /**
     * @param LoggerInterface $logger
     * @return SzApiLogSubscriber
     */
    public function setLogger(LoggerInterface $logger): SzApiLogSubscriber
    {
        $this->logger = $logger;
        return $this;
    }

    /**
     * @return \DateTimeImmutable
     */
    public function getStartDateTime(): \DateTimeImmutable
    {
        return $this->startDateTime;
    }


    /**
     * @return string
     */
    public function getRequestUri(): string
    {
        return $this->requestUri;
    }

    /**
     * @param string $requestUri
     * @return SzApiLogSubscriber
     */
    public function setRequestUri(string $requestUri): SzApiLogSubscriber
    {
        $this->requestUri = $requestUri;
        return $this;
    }

    /**
     * @return string
     */
    public function getHost(): string
    {
        return $this->host;
    }

    private function setRequestHeaders(\ArrayIterator $requestHeaders)
    {
        $this->requestHeaders = $requestHeaders;
    }

    /**
     * @return string
     */
    public function getRequestUid(): string
    {
        return $this->requestUid;
    }

    /**
     * @return \ArrayIterator
     */
    public function getRequestHeaders(): \ArrayIterator
    {
        return $this->requestHeaders;
    }

    private function setRequestBody(string $requestBody)
    {
        $this->requestBody = $requestBody;

        return $this;
    }

    /**
     * @return string
     */
    public function getRequestBody(): string
    {
        return $this->requestBody;
    }

    /**
     * @return \ArrayIterator
     */
    public function getResponseHeaders(): \ArrayIterator
    {
        return $this->responseHeaders;
    }

    /**
     * @param \ArrayIterator $responseHeaders
     * @return SzApiLogSubscriber
     */
    public function setResponseHeaders(\ArrayIterator $responseHeaders): SzApiLogSubscriber
    {
        $this->responseHeaders = $responseHeaders;
        return $this;
    }

    /**
     * @return string
     */
    public function getResponseBody(): string
    {
        return $this->responseBody;
    }

    /**
     * @param string $responseBody
     * @return SzApiLogSubscriber
     */
    public function setResponseBody(string $responseBody): SzApiLogSubscriber
    {
        $this->responseBody = $responseBody;

        return $this;
    }

    /**
     * @return float|int
     */
    public function getDuration()
    {
        return $this->duration;
    }

    /**
     * @param float|int $duration
     * @return SzApiLogSubscriber
     */
    public function setDuration($duration)
    {
        $this->duration = $duration;

        return $this;
    }

    /**
     * @return \DateTimeImmutable
     */
    public function getEndDateTime(): \DateTimeImmutable
    {
        return $this->endDateTime;
    }

    /**
     * @param \DateTimeImmutable $endDateTime
     * @return SzApiLogSubscriber
     */
    public function setEndDateTime(\DateTimeImmutable $endDateTime): SzApiLogSubscriber
    {
        $this->endDateTime = $endDateTime;

        return $this;
    }

    /**
     * @param PostResponseEvent $event
     * @param \Symfony\Component\Stopwatch\StopwatchEvent $swEvent
     * @return void
     */
    public function keepResponseData(PostResponseEvent $event, StopwatchEvent $swEvent): void
    {
        $this->setResponseHeaders($event->getResponse()->headers->getIterator());
        $this->setResponseBody($event->getResponse()->getContent());
        $this->setEndDateTime(new \DateTimeImmutable());
        $this->setDuration($swEvent->getDuration());
    }

    /**
     * @return void
     */
    public function logRequest(): void
    {
        $this->logger->info(
            sprintf('Api Request #%s', $this->getRequestUid()),
            [
                'uid' => $this->getRequestUid(),
                'host' => $this->getHost(),
                'uri' => $this->getRequestUri(),
                'date' => $this->getStartDateTime()->format("y:m:d h:i:s"),
                'type' => 'request', //Const
                'headers' => iterator_to_array($this->getRequestHeaders()),
                'data' => $this->getRequestBody(),
                'duration' => 0
            ]
        );
    }

    /**
     * @return void
     */
    public function logResponse(): void
    {
        $this->logger->info(
            sprintf('Api Response #%s', $this->getRequestUid()),
            [
                'uid' => $this->getRequestUid(),
                'host' => $this->getHost(),
                'uri' => $this->getRequestUri(),
                'date' => $this->getEndDateTime()->format("y:m:d h:i:s"),
                'type' => 'response', //const
                'headers' => iterator_to_array($this->getResponseHeaders()),
                'data' => $this->getResponseBody(),
                'duration' => $this->getDuration()
            ]
        );
    }
}
